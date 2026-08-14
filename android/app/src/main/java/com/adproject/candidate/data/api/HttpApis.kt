package com.adproject.candidate.data.api

import com.adproject.candidate.data.contract.AuthData
import com.adproject.candidate.data.contract.CandidateRegisterRequest
import com.adproject.candidate.data.contract.Company
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.CursorMeta
import com.adproject.candidate.data.contract.DataEnvelope
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.ListEnvelope
import com.adproject.candidate.data.contract.LoginRequest
import com.adproject.candidate.data.contract.MatchAnalysis
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.PageMeta
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.RecruiterContact
import com.adproject.candidate.data.contract.RefreshTokenRequest
import com.adproject.candidate.data.contract.Salary
import com.adproject.candidate.data.contract.SendMessageRequest
import com.adproject.candidate.data.contract.TokenData
import com.adproject.candidate.data.contract.Visibility
import com.adproject.candidate.data.contract.WorkplaceType
import com.adproject.candidate.data.contract.JobStatus
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header

interface AuthHttpApi {
    @POST("auth/register") suspend fun register(@Body request: CandidateRegisterRequest): Response<DataEnvelope<AuthData>>
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): Response<DataEnvelope<AuthData>>
    @POST("auth/refresh") suspend fun refresh(@Body request: RefreshTokenRequest): Response<DataEnvelope<TokenData>>
    @POST("auth/logout") suspend fun logout(@Body request: RefreshTokenRequest): Response<Unit>
}

interface CandidateJobHttpApi {
    @GET("jobs")
    suspend fun jobs(
        @Query("q") q: String?,
        @Query("employmentType") employmentType: EmploymentType?,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ListEnvelope<NetworkCandidateJob, PageMeta>>

    @GET("jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): Response<DataEnvelope<NetworkCandidateJob>>
}

interface CandidateProfileHttpApi {
    @GET("candidate/profile") suspend fun get(): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateProfileDto>>
    @PATCH("candidate/profile") suspend fun update(@Body request: com.adproject.candidate.data.contract.UpdateProfileRequest): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateProfileDto>>
}

interface CandidateResumeHttpApi {
    @GET("candidate/resume") suspend fun get(): Response<DataEnvelope<com.adproject.candidate.data.contract.Resume>>
    @PUT("candidate/resume") suspend fun save(@Body request: com.adproject.candidate.data.contract.SaveResumeRequest): Response<DataEnvelope<com.adproject.candidate.data.contract.Resume>>
}

interface CandidateApplicationHttpApi {
    @GET("candidate/applications")
    suspend fun applications(
        @Query("filter") filter: com.adproject.candidate.data.contract.ApplicationListFilter?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<ListEnvelope<com.adproject.candidate.data.contract.CandidateApplicationSummary,
            com.adproject.candidate.data.contract.CandidateApplicationListMeta>>

    @GET("candidate/applications/{applicationId}")
    suspend fun application(
        @Path("applicationId") applicationId: String,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateApplication>>

    @POST("candidate/applications/{applicationId}/withdraw")
    suspend fun withdraw(
        @Path("applicationId") applicationId: String,
        @Body request: com.adproject.candidate.data.contract.WithdrawApplicationRequest,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateApplication>>

    @POST("jobs/{jobId}/applications")
    suspend fun submit(
        @Path("jobId") jobId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: com.adproject.candidate.data.contract.SubmitApplicationRequest,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateApplication>>
}

interface CandidateConversationHttpApi {
    @GET("candidate/conversations")
    suspend fun conversations(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ListEnvelope<ConversationSummary, PageMeta>>

    @GET("candidate/conversations/{conversationId}")
    suspend fun conversation(@Path("conversationId") conversationId: String): Response<DataEnvelope<ConversationDetail>>

    @GET("candidate/conversations/{conversationId}/messages")
    suspend fun messages(
        @Path("conversationId") conversationId: String,
        @Query("before") before: String?,
        @Query("limit") limit: Int = 30,
    ): Response<ListEnvelope<Message, CursorMeta>>

    @POST("candidate/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendMessageRequest,
    ): Response<DataEnvelope<Message>>

    @PUT("candidate/conversations/{conversationId}/read-state")
    suspend fun markRead(
        @Path("conversationId") conversationId: String,
        @Body request: ReadStateRequest,
    ): Response<Unit>
}

data class NetworkCandidateJob(
    val jobId: String,
    val title: String,
    val company: Company,
    val employmentType: EmploymentType,
    val workplaceType: WorkplaceType,
    val location: String,
    val salary: Salary,
    val description: String,
    val requirements: List<String>,
    val skills: List<String>,
    val deadline: String?,
    val visibility: Visibility,
    val status: JobStatus,
    val publishedAt: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val matchScore: Int?,
    val recruiter: RecruiterContact?,
    val matchAnalysis: MatchAnalysis? = null,
    val applicationState: String? = null,
    val isSaved: Boolean? = null,
)
