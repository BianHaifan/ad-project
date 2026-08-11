package com.adproject.candidate.data.api

import com.adproject.candidate.core.auth.SessionManager
import com.adproject.candidate.data.contract.CandidateJob
import com.adproject.candidate.data.contract.CandidateJobApplicationState
import com.adproject.candidate.data.contract.CandidateJobDetail
import com.adproject.candidate.data.contract.CandidateRegisterRequest
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.ErrorEnvelope
import com.adproject.candidate.data.contract.LoginRequest
import com.adproject.candidate.data.contract.PageMeta
import com.adproject.candidate.data.contract.RefreshTokenRequest
import com.adproject.candidate.data.contract.CandidateApplication
import com.adproject.candidate.data.contract.SubmitApplicationRequest
import com.squareup.moshi.Moshi
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val statusCode: Int? = null,
        val code: String? = null,
    ) : ApiResult<Nothing>
}

interface AuthRepository {
    suspend fun login(email: String, password: String): ApiResult<Unit>
    suspend fun register(fullName: String, email: String, password: String): ApiResult<Unit>
    suspend fun logout(): ApiResult<Unit>
}

class RealAuthRepository(
    private val publicApi: AuthHttpApi,
    private val authenticatedApi: AuthHttpApi,
    private val sessionManager: SessionManager,
    moshi: Moshi,
) : AuthRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun login(email: String, password: String): ApiResult<Unit> = callAuth {
        publicApi.login(LoginRequest(email, password))
    }

    override suspend fun register(fullName: String, email: String, password: String): ApiResult<Unit> = callAuth {
        publicApi.register(CandidateRegisterRequest(fullName = fullName, email = email, password = password,
            acceptedTermsVersion = "2026-08"))
    }

    override suspend fun logout(): ApiResult<Unit> {
        val refresh = sessionManager.tokens()?.refreshToken
        if (refresh == null) {
            sessionManager.clear()
            return ApiResult.Success(Unit)
        }
        return try {
            val response = authenticatedApi.logout(RefreshTokenRequest(refresh))
            sessionManager.clear()
            if (response.isSuccessful) ApiResult.Success(Unit) else errors.failure(response.code(), response.errorBody()?.string())
        } catch (_: Exception) {
            sessionManager.clear()
            ApiResult.Failure("Unable to sign out from the server. Your local session was cleared.")
        }
    }

    private suspend fun callAuth(call: suspend () -> retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.AuthData>>): ApiResult<Unit> {
        return try {
            val response = call()
            val data = response.body()?.data
            if (!response.isSuccessful || data == null) return errors.failure(response.code(), response.errorBody()?.string())
            if (data.user.role.name != "CANDIDATE") return ApiResult.Failure("Please use a Candidate account.")
            sessionManager.save(data.accessToken, data.refreshToken)
            ApiResult.Success(Unit)
        } catch (_: IOException) {
            ApiResult.Failure("Unable to connect. Check your network and try again.")
        } catch (_: Exception) {
            ApiResult.Failure("Something went wrong. Please try again.")
        }
    }
}

data class CandidateJobPage(val jobs: List<CandidateJob>, val meta: PageMeta)

interface CandidateJobRepository {
    suspend fun jobs(q: String?, employmentType: EmploymentType?): ApiResult<CandidateJobPage>
    suspend fun job(jobId: String): ApiResult<CandidateJobDetail>
}

class RealCandidateJobRepository(
    private val api: CandidateJobHttpApi,
    moshi: Moshi,
) : CandidateJobRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun jobs(q: String?, employmentType: EmploymentType?): ApiResult<CandidateJobPage> = try {
        val response = api.jobs(q?.trim()?.takeIf { it.isNotEmpty() }, employmentType)
        val body = response.body()
        if (!response.isSuccessful || body == null) errors.failure(response.code(), response.errorBody()?.string())
        else ApiResult.Success(CandidateJobPage(body.data.map(::toCandidateJob), body.meta))
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load jobs. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load jobs right now.")
    }

    override suspend fun job(jobId: String): ApiResult<CandidateJobDetail> = try {
        val response = api.job(jobId)
        val body = response.body()?.data
        if (!response.isSuccessful || body == null) errors.failure(response.code(), response.errorBody()?.string())
        else ApiResult.Success(CandidateJobDetail(
            job = toCandidateJob(body),
            matchAnalysis = body.matchAnalysis,
            applicationState = CandidateJobApplicationState.valueOf(body.applicationState ?: "NOT_APPLIED"),
            isSaved = body.isSaved ?: false,
        ))
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load this job. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load this job right now.")
    }

    private fun toCandidateJob(job: NetworkCandidateJob) = CandidateJob(
        job.jobId, job.title, job.company, job.employmentType, job.workplaceType, job.location, job.salary,
        job.description, job.requirements, job.skills, job.deadline, job.visibility, job.status, job.publishedAt,
        job.version, job.createdAt, job.updatedAt, job.matchScore, job.recruiter,
    )
}

class ApiErrorParser(moshi: Moshi) {
    private val adapter = moshi.adapter(ErrorEnvelope::class.java)

    fun failure(statusCode: Int, body: String?): ApiResult.Failure {
        val error = body?.let { runCatching { adapter.fromJson(it)?.error }.getOrNull() }
        val safe = when (statusCode) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You do not have permission to perform this action."
            404 -> "This job is no longer available."
            in 500..599 -> "The service is temporarily unavailable. Please try again."
            else -> error?.message?.takeIf { it.isNotBlank() } ?: "Request failed. Please try again."
        }
        return ApiResult.Failure(safe, error?.fieldErrors.orEmpty(), statusCode, error?.code)
    }
}

interface CandidateProfileRepository {
    suspend fun get(): ApiResult<com.adproject.candidate.data.contract.CandidateProfileDto>
    suspend fun update(request: com.adproject.candidate.data.contract.UpdateProfileRequest): ApiResult<com.adproject.candidate.data.contract.CandidateProfileDto>
}
class RealCandidateProfileRepository(private val api: CandidateProfileHttpApi, moshi: Moshi) : CandidateProfileRepository {
    private val errors=ApiErrorParser(moshi)
    override suspend fun get()=call { api.get() }
    override suspend fun update(request: com.adproject.candidate.data.contract.UpdateProfileRequest)=call { api.update(request) }
    private suspend fun call(block:suspend()->retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.CandidateProfileDto>>):ApiResult<com.adproject.candidate.data.contract.CandidateProfileDto> = try { val r=block(); val d=r.body()?.data; if(r.isSuccessful&&d!=null) ApiResult.Success(d) else errors.failure(r.code(),r.errorBody()?.string()) } catch(_:IOException){ApiResult.Failure("Unable to load your profile. Check your network and try again.")} catch(_:Exception){ApiResult.Failure("Unable to load your profile right now.")}
}
interface CandidateResumeRepository {
    suspend fun get(): ApiResult<com.adproject.candidate.data.contract.Resume>
    suspend fun save(request: com.adproject.candidate.data.contract.SaveResumeRequest): ApiResult<com.adproject.candidate.data.contract.Resume>
}
class RealCandidateResumeRepository(private val api: CandidateResumeHttpApi, moshi: Moshi) : CandidateResumeRepository {
    private val errors=ApiErrorParser(moshi)
    override suspend fun get()=call { api.get() }
    override suspend fun save(request: com.adproject.candidate.data.contract.SaveResumeRequest)=call { api.save(request) }
    private suspend fun call(block:suspend()->retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.Resume>>):ApiResult<com.adproject.candidate.data.contract.Resume> = try { val r=block(); val d=r.body()?.data; if(r.isSuccessful&&d!=null) ApiResult.Success(d) else if(r.code()==404) ApiResult.Failure("No resume has been created yet.",statusCode=404) else errors.failure(r.code(),r.errorBody()?.string()) } catch(_:IOException){ApiResult.Failure("Unable to load your resume. Check your network and try again.")} catch(_:Exception){ApiResult.Failure("Unable to load your resume right now.")}
}

interface CandidateApplicationRepository {
    suspend fun submit(jobId: String, idempotencyKey: String,
                       request: SubmitApplicationRequest): ApiResult<CandidateApplication>
}

class RealCandidateApplicationRepository(
    private val api: CandidateApplicationHttpApi,
    moshi: Moshi,
) : CandidateApplicationRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun submit(jobId: String, idempotencyKey: String,
                                request: SubmitApplicationRequest): ApiResult<CandidateApplication> = try {
        val response = api.submit(jobId, idempotencyKey, request)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else {
            val failure = errors.failure(response.code(), response.errorBody()?.string())
            when (failure.code) {
                "APPLICATION_ALREADY_EXISTS" -> failure.copy(message = "You have already applied for this job.")
                "IDEMPOTENCY_KEY_REUSED" -> failure.copy(message = "This submission could not be retried safely. Please return to the job and try again.")
                else -> failure
            }
        }
    } catch (_: IOException) {
        ApiResult.Failure("Unable to submit your application. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to submit your application right now.")
    }
}
