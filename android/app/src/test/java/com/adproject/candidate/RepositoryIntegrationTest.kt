package com.adproject.candidate

import com.adproject.candidate.core.auth.SessionManager
import com.adproject.candidate.core.auth.SessionTokens
import com.adproject.candidate.core.auth.TokenStore
import com.adproject.candidate.core.network.AccessTokenInterceptor
import com.adproject.candidate.core.network.RefreshAuthenticator
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AuthHttpApi
import com.adproject.candidate.data.api.CandidateJobHttpApi
import com.adproject.candidate.data.api.RealAuthRepository
import com.adproject.candidate.data.api.RealCandidateJobRepository
import com.adproject.candidate.data.api.CandidateProfileHttpApi
import com.adproject.candidate.data.api.CandidateResumeHttpApi
import com.adproject.candidate.data.api.RealCandidateProfileRepository
import com.adproject.candidate.data.api.RealCandidateResumeRepository
import com.adproject.candidate.data.api.CandidateApplicationHttpApi
import com.adproject.candidate.data.api.RealCandidateApplicationRepository
import com.adproject.candidate.data.contract.SubmitApplicationRequest
import com.adproject.candidate.data.contract.ApplicationStatus
import com.adproject.candidate.data.contract.ApplicationListFilter
import com.adproject.candidate.data.contract.WithdrawApplicationRequest
import com.adproject.candidate.data.contract.UpdateProfileRequest
import com.adproject.candidate.data.contract.SaveResumeRequest
import com.adproject.candidate.data.contract.EmploymentType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class RepositoryIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var moshi: Moshi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }

    @After fun tearDown() = server.shutdown()

    @Test fun loginAndRegistrationSaveRealCandidateTokens() = runTest {
        val store = MemoryTokenStore()
        val publicApi = retrofit().create(AuthHttpApi::class.java)
        val session = SessionManager(store, publicApi)
        val repository = RealAuthRepository(publicApi, publicApi, session, moshi)
        server.enqueue(jsonResponse(authBody("access-login", "refresh-login")))
        assertTrue(repository.login("candidate@example.com", "UnitOnly9!") is ApiResult.Success)
        assertEquals(SessionTokens("access-login", "refresh-login"), store.value)
        assertTrue(server.takeRequest().body.readUtf8().contains("candidate@example.com"))

        server.enqueue(jsonResponse(authBody("access-register", "refresh-register")))
        assertTrue(repository.register("Candidate", "new@example.com", "UnitOnly9!") is ApiResult.Success)
        val registration = server.takeRequest().body.readUtf8()
        assertTrue(registration.contains("\"role\":\"CANDIDATE\""))
        assertFalse(registration.contains("companyName"))
    }

    @Test fun authFieldErrorsAndSafeLoginFailureAreMapped() = runTest {
        val api = retrofit().create(AuthHttpApi::class.java)
        val repository = RealAuthRepository(api, api, SessionManager(MemoryTokenStore(), api), moshi)
        server.enqueue(jsonResponse(errorBody("VALIDATION_ERROR", "Request validation failed", "email"), 422))
        val validation = repository.register("Candidate", "bad", "UnitOnly9!") as ApiResult.Failure
        assertEquals("invalid", validation.fieldErrors["email"])

        server.enqueue(jsonResponse(errorBody("UNAUTHORIZED", "internal authentication detail"), 401))
        val login = repository.login("candidate@example.com", "wrong-password") as ApiResult.Failure
        assertEquals("Your session has expired. Please sign in again.", login.message)
        assertFalse(login.message.contains("internal"))
    }

    @Test fun jobsExposeContentEmptyErrorAndDetail404WithoutFakeScores() = runTest {
        val repository = RealCandidateJobRepository(retrofit().create(CandidateJobHttpApi::class.java), moshi)
        server.enqueue(jsonResponse(jobPageBody()))
        val page = repository.jobs("Backend", EmploymentType.FULL_TIME) as ApiResult.Success
        assertEquals(1, page.value.jobs.size)
        assertNull(page.value.jobs.first().matchScore)
        assertNull(page.value.jobs.first().recruiter)
        assertTrue(server.takeRequest().path!!.contains("q=Backend"))

        server.enqueue(jsonResponse("""{"data":[],"meta":{"page":1,"pageSize":20,"total":0,"hasNext":false}}"""))
        val empty = repository.jobs(null, null) as ApiResult.Success
        assertTrue(empty.value.jobs.isEmpty())

        server.enqueue(jsonResponse(errorBody("INTERNAL_ERROR", "database trace"), 500))
        val failure = repository.jobs(null, null) as ApiResult.Failure
        assertFalse(failure.message.contains("trace"))

        server.enqueue(jsonResponse(errorBody("NOT_FOUND", "Job not found"), 404))
        val missing = repository.job("missing") as ApiResult.Failure
        assertEquals(404, missing.statusCode)
        assertEquals("This job is no longer available.", missing.message)
    }

    @Test fun detailMapsTransitionalApplicationAndSavedState() = runTest {
        val repository = RealCandidateJobRepository(retrofit().create(CandidateJobHttpApi::class.java), moshi)
        server.enqueue(jsonResponse("""{"data":${jobObject()},"matchAnalysis":null}""".replace(
            "\"updatedAt\":\"2026-08-11T08:00:00Z\"",
            "\"updatedAt\":\"2026-08-11T08:00:00Z\",\"matchAnalysis\":null,\"applicationState\":\"NOT_APPLIED\",\"isSaved\":false",
        )))
        val result = repository.job("job-1") as ApiResult.Success
        assertEquals("NOT_APPLIED", result.value.applicationState.name)
        assertFalse(result.value.isSaved)
        assertNull(result.value.matchAnalysis)
    }

    @Test fun unauthorizedRequestRefreshesOnceSavesRotatedTokensAndRetries() = runTest {
        val store = MemoryTokenStore(SessionTokens("old-access", "old-refresh"))
        val refreshApi = retrofit().create(AuthHttpApi::class.java)
        val session = SessionManager(store, refreshApi)
        val client = OkHttpClient.Builder().addInterceptor(AccessTokenInterceptor(session))
            .authenticator(RefreshAuthenticator(session)).build()
        val jobs = retrofit(client).create(CandidateJobHttpApi::class.java)
        server.enqueue(jsonResponse(errorBody("UNAUTHORIZED", "expired"), 401))
        server.enqueue(jsonResponse("""{"data":{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":7200,"refreshExpiresIn":2592000}}"""))
        server.enqueue(jsonResponse(jobPageBody()))

        assertTrue(jobs.jobs(null, null).isSuccessful)
        assertEquals(SessionTokens("new-access", "new-refresh"), store.value)
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun concurrentRefreshIsCoordinatedAndRefreshFailureClearsSession() = runTest {
        val store = MemoryTokenStore(SessionTokens("old", "refresh"))
        val api = retrofit().create(AuthHttpApi::class.java)
        val session = SessionManager(store, api)
        server.enqueue(jsonResponse("""{"data":{"accessToken":"rotated","refreshToken":"rotated-refresh","expiresIn":7200,"refreshExpiresIn":2592000}}"""))
        val first = async { session.refreshAfterUnauthorized("old") }
        val second = async { session.refreshAfterUnauthorized("old") }
        assertEquals("rotated", first.await())
        assertEquals("rotated", second.await())
        assertEquals(1, server.requestCount)

        server.enqueue(jsonResponse(errorBody("UNAUTHORIZED", "expired"), 401))
        assertNull(session.refreshAfterUnauthorized("rotated"))
        assertNull(store.value)
        assertEquals(false, session.sessionActive.value)
    }

    @Test fun profileRepositoryLoadsUpdatesAndMapsFieldErrors() = runTest {
        val repository = RealCandidateProfileRepository(retrofit().create(CandidateProfileHttpApi::class.java), moshi)
        val profile = """{"userId":"candidate-1","fullName":"Candidate","email":"candidate@example.com","headline":"Engineer","avatarUrl":null,"location":"Singapore","stats":{"chatCount":0,"applicationCount":0,"interviewCount":0,"savedJobCount":0},"version":1,"createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z"}"""
        server.enqueue(jsonResponse("""{"data":$profile}"""))
        assertEquals("Candidate", (repository.get() as ApiResult.Success).value.fullName)
        server.takeRequest()
        server.enqueue(jsonResponse("""{"data":${profile.replace("\"version\":1", "\"version\":2")}}"""))
        val updated = repository.update(UpdateProfileRequest("Candidate", "Engineer", "Singapore", 1)) as ApiResult.Success
        assertEquals(2, updated.value.version)
        assertFalse(server.takeRequest().body.readUtf8().contains("userId"))
        server.enqueue(jsonResponse(errorBody("VALIDATION_ERROR", "Request validation failed", "headline"), 422))
        val error = repository.update(UpdateProfileRequest("Candidate", "x", "Singapore", 2)) as ApiResult.Failure
        assertEquals("invalid", error.fieldErrors["headline"])
    }

    @Test fun resumeRepositoryHandlesMissingCreateAndRotatingVersions() = runTest {
        val repository = RealCandidateResumeRepository(retrofit().create(CandidateResumeHttpApi::class.java), moshi)
        server.enqueue(jsonResponse(errorBody("NOT_FOUND", "Resume not found"), 404))
        assertEquals(404, (repository.get() as ApiResult.Failure).statusCode)
        server.takeRequest()
        val resume = """{"resumeId":"resume-1","fullName":"Candidate","age":27,"location":"Singapore","headline":"Engineer","summary":"Summary","experiences":[],"version":1,"createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z"}"""
        server.enqueue(jsonResponse("""{"data":$resume}"""))
        val saved = repository.save(SaveResumeRequest("Candidate", 27, "Singapore", "Engineer", "Summary", emptyList(), 0)) as ApiResult.Success
        assertEquals(1, saved.value.version)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"expectedVersion\":0"))
        assertFalse(body.contains("candidateId"))
    }

    @Test fun applicationSubmissionUsesHeaderRealFieldsAndMapsRealResultAndConflicts() = runTest {
        val repository = RealCandidateApplicationRepository(
            retrofit().create(CandidateApplicationHttpApi::class.java), moshi,
        )
        server.enqueue(jsonResponse(applicationBody()))
        val key = "550e8400-e29b-41d4-a716-446655440000"
        val result = repository.submit("job-1", key,
            SubmitApplicationRequest("resume-1", "candidate@example.com", true)) as ApiResult.Success
        assertEquals("application-1", result.value.applicationId)
        assertEquals(ApplicationStatus.APPLIED, result.value.status)
        assertEquals("snapshot-1", result.value.resumeSnapshot.snapshotId)
        assertEquals("Recruiter review", result.value.nextSteps.first().title)
        val request = server.takeRequest()
        assertEquals(key, request.getHeader("Idempotency-Key"))
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("\"resumeId\":\"resume-1\""))
        assertTrue(requestBody.contains("\"contactEmail\":\"candidate@example.com\""))
        assertTrue(requestBody.contains("\"shareProfile\":true"))
        assertFalse(requestBody.contains("candidateId"))
        assertFalse(requestBody.contains("status"))
        assertFalse(requestBody.contains("version"))

        server.enqueue(jsonResponse(errorBody("APPLICATION_ALREADY_EXISTS", "internal duplicate"), 409))
        val duplicate = repository.submit("job-1", java.util.UUID.randomUUID().toString(),
            SubmitApplicationRequest("resume-1", "candidate@example.com", true)) as ApiResult.Failure
        assertEquals("You have already applied for this job.", duplicate.message)
        server.takeRequest()
        server.enqueue(jsonResponse(errorBody("IDEMPOTENCY_KEY_REUSED", "internal idempotency detail"), 409))
        val conflict = repository.submit("job-1", key,
            SubmitApplicationRequest("resume-1", "candidate@example.com", false)) as ApiResult.Failure
        assertFalse(conflict.message.contains("internal"))
    }

    @Test fun applicationListDetailAndWithdrawUseExactRealContract() = runTest {
        val repository = RealCandidateApplicationRepository(
            retrofit().create(CandidateApplicationHttpApi::class.java), moshi,
        )
        server.enqueue(jsonResponse(applicationListBody()))
        val list = repository.applications(ApplicationListFilter.ARCHIVED, 2, 10) as ApiResult.Success
        assertEquals(1, list.value.applications.size)
        assertEquals(3, list.value.meta.counts.archived)
        assertNull(list.value.applications.single().matchScore)
        assertNull(list.value.applications.single().scheduledAt)
        val listRequest = server.takeRequest()
        assertTrue(listRequest.path!!.contains("filter=ARCHIVED"))
        assertTrue(listRequest.path!!.contains("page=2"))
        assertTrue(listRequest.path!!.contains("pageSize=10"))

        server.enqueue(jsonResponse(applicationBody()))
        val detail = repository.application("application-1") as ApiResult.Success
        assertEquals("snapshot-1", detail.value.resumeSnapshot.snapshotId)
        assertNull(detail.value.interview)
        assertEquals("/api/v1/candidate/applications/application-1", server.takeRequest().path)

        server.enqueue(jsonResponse(applicationBody().replace("\"status\":\"APPLIED\"", "\"status\":\"WITHDRAWN\"")
            .replace("\"version\":1", "\"version\":2")))
        val withdrawn = repository.withdraw("application-1", WithdrawApplicationRequest("Changed plans", 1))
                as ApiResult.Success
        assertEquals(ApplicationStatus.WITHDRAWN, withdrawn.value.status)
        assertEquals(2, withdrawn.value.version)
        val withdrawRequest = server.takeRequest()
        assertEquals("/api/v1/candidate/applications/application-1/withdraw", withdrawRequest.path)
        val withdrawBody = withdrawRequest.body.readUtf8()
        assertTrue(withdrawBody.contains("\"reason\":\"Changed plans\""))
        assertTrue(withdrawBody.contains("\"expectedVersion\":1"))
        assertFalse(withdrawBody.contains("candidateId"))
        assertFalse(withdrawBody.contains("targetStatus"))

        server.enqueue(jsonResponse(errorBody("VERSION_CONFLICT", "internal version detail"), 409))
        val conflict = repository.withdraw("application-1", WithdrawApplicationRequest("Changed plans", 1))
                as ApiResult.Failure
        assertEquals("This application changed. Refresh before trying again.", conflict.message)
        assertFalse(conflict.message.contains("internal"))
    }

    private fun retrofit(client: OkHttpClient = OkHttpClient()): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/api/v1/"))
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    private fun errorBody(code: String, message: String, field: String? = null): String =
        """{"error":{"code":"$code","message":"$message","fieldErrors":${if (field == null) "{}" else "{\"$field\":\"invalid\"}"},"requestId":"req-test"}}"""

    private fun authBody(access: String, refresh: String) = """
        {"data":{"accessToken":"$access","refreshToken":"$refresh","expiresIn":7200,"refreshExpiresIn":2592000,
        "user":{"userId":"candidate-1","role":"CANDIDATE","fullName":"Candidate","email":"candidate@example.com",
        "avatarUrl":null,"createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z","company":null}}}
    """.trimIndent()

    private fun jobPageBody() = """{"data":[${jobObject()}],"meta":{"page":1,"pageSize":20,"total":1,"hasNext":false}}"""
    private fun jobObject() = """
        {"jobId":"job-1","title":"Backend Engineer","company":{"companyId":"company-1","name":"Real Company",
        "logoUrl":null,"stage":null,"employeeRange":null,"verificationStatus":"APPROVED","website":null,
        "description":null,"location":null,"version":1,"createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z"},
        "employmentType":"FULL_TIME","workplaceType":"HYBRID","location":"Singapore",
        "salary":{"min":5000,"max":8000,"currency":"SGD","period":"MONTH"},"description":"Real role",
        "requirements":["Reliable APIs"],"skills":["Java"],"deadline":null,"visibility":"PUBLIC","status":"ACTIVE",
        "publishedAt":"2026-08-11T08:00:00Z","version":2,"createdAt":"2026-08-11T08:00:00Z",
        "updatedAt":"2026-08-11T08:00:00Z","matchScore":null,"recruiter":null}
    """.trimIndent()

    private fun applicationBody() = """
        {"data":{"applicationId":"application-1","jobId":"job-1","status":"APPLIED",
        "appliedAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z","version":1,
        "jobTitle":"Backend Engineer","company":{"companyId":"company-1","name":"Real Company",
        "logoUrl":null,"stage":null,"employeeRange":null,"verificationStatus":"APPROVED","website":null,
        "description":null,"location":null,"version":1,"createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z"},
        "matchScore":null,"scheduledAt":null,"timeline":[{"status":"APPLIED","completed":true,"occurredAt":"2026-08-11T08:00:00Z"}],
        "resumeSnapshot":{"snapshotId":"snapshot-1","capturedAt":"2026-08-11T08:00:00Z","resumeId":"resume-1",
        "fullName":"Candidate","age":27,"location":"Singapore","headline":"Engineer","summary":"Summary",
        "experiences":[],"version":1,"createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z"},
        "interview":null,"nextSteps":[{"type":"RECRUITER_REVIEW","title":"Recruiter review","description":"Review"}]}}
    """.trimIndent()

    private fun applicationListBody() = """
        {"data":[{"applicationId":"application-1","jobId":"job-1","status":"WITHDRAWN",
        "appliedAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T09:00:00Z","version":2,
        "jobTitle":"Backend Engineer","company":{"companyId":"company-1","name":"Real Company",
        "logoUrl":null,"stage":null,"employeeRange":null,"verificationStatus":"APPROVED","website":null,
        "description":null,"location":null,"version":1,"createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T08:00:00Z"},
        "matchScore":null,"scheduledAt":null,"timeline":[{"status":"APPLIED","completed":true,"occurredAt":"2026-08-11T08:00:00Z"},
        {"status":"WITHDRAWN","completed":true,"occurredAt":"2026-08-11T09:00:00Z"}]}],
        "meta":{"page":2,"pageSize":10,"total":1,"hasNext":false,"counts":{"active":1,"interview":2,"archived":3}}}
    """.trimIndent()
}

private class MemoryTokenStore(initial: SessionTokens? = null) : TokenStore {
    var value: SessionTokens? = initial
    override suspend fun read() = value
    override suspend fun write(tokens: SessionTokens) { value = tokens }
    override suspend fun clear() { value = null }
}
