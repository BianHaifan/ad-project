package com.adproject.candidate.data.contract

object CandidateApiPaths {
    const val REGISTER = "/auth/register"
    const val LOGIN = "/auth/login"
    const val REFRESH = "/auth/refresh"
    const val LOGOUT = "/auth/logout"
    const val JOBS = "/jobs"
    const val APPLICATIONS = "/candidate/applications"
    const val CONVERSATIONS = "/candidate/conversations"
    const val PROFILE = "/candidate/profile"
    const val RESUME = "/candidate/resume"
    const val LEARNING = "/features/learning"

    fun job(jobId: String) = "/jobs/$jobId"
    fun submitApplication(jobId: String) = "/jobs/$jobId/applications"
    fun application(applicationId: String) = "$APPLICATIONS/$applicationId"
    fun withdrawApplication(applicationId: String) = "$APPLICATIONS/$applicationId/withdraw"
    fun conversation(conversationId: String) = "$CONVERSATIONS/$conversationId"
    fun messages(conversationId: String) = "${conversation(conversationId)}/messages"
    fun readState(conversationId: String) = "${conversation(conversationId)}/read-state"
}

data class DataEnvelope<T>(val data: T)
data class ListEnvelope<T, M>(val data: List<T>, val meta: M)
data class ErrorEnvelope(val error: ApiError)
data class ApiError(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String>,
    val requestId: String,
)

data class PageMeta(val page: Int, val pageSize: Int, val total: Int, val hasNext: Boolean)
data class CursorMeta(val nextCursor: String?, val hasMore: Boolean)

enum class UserRole { CANDIDATE, RECRUITER, ADMIN }
enum class ApplicationStatus { APPLIED, IN_REVIEW, INTERVIEW, REJECTED, WITHDRAWN }
enum class CandidateJobApplicationState { NOT_APPLIED, APPLIED, IN_REVIEW, INTERVIEW, REJECTED, WITHDRAWN }
enum class ApplicationListFilter { ACTIVE, INTERVIEW, ARCHIVED }
enum class JobStatus { DRAFT, ACTIVE, PAUSED, CLOSED }
enum class InterviewStatus { SCHEDULED, COMPLETED, CANCELLED }
enum class InterviewMode { ONLINE, ONSITE, PHONE }
enum class SenderType { CANDIDATE, RECRUITER, SYSTEM }
enum class DeliveryStatus { SENDING, SENT, DELIVERED, READ, FAILED }
enum class EmploymentType { FULL_TIME, INTERNSHIP, PART_TIME }
enum class WorkplaceType { ONSITE, HYBRID, REMOTE }
enum class Visibility { PUBLIC, PRIVATE }

data class User(
    val userId: String,
    val role: UserRole,
    val fullName: String,
    val email: String,
    val avatarUrl: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class Company(
    val companyId: String,
    val name: String,
    val logoUrl: String?,
    val stage: String?,
    val employeeRange: String?,
    val verificationStatus: String?,
    val website: String?,
    val description: String?,
    val location: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class RecruiterContact(
    val recruiterId: String,
    val fullName: String,
    val title: String,
    val avatarUrl: String?,
)

data class Salary(val min: Int, val max: Int, val currency: String = "SGD", val period: String)

data class CandidateJob(
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
)

data class CandidateJobDetail(
    val job: CandidateJob,
    val matchAnalysis: MatchAnalysis?,
    val applicationState: CandidateJobApplicationState,
    val isSaved: Boolean,
)

data class MatchAnalysis(
    val score: Int,
    val evidence: List<String>,
    val strongMatches: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    val modelVersion: String,
    val generatedAt: String,
)

data class Experience(
    val experienceId: String?,
    val title: String,
    val company: String,
    val description: String,
    val startDate: String,
    val endDate: String?,
)

open class Resume(
    open val resumeId: String,
    open val fullName: String,
    open val age: Int,
    open val location: String,
    open val headline: String,
    open val summary: String,
    open val experiences: List<Experience>,
    open val version: Int,
    open val createdAt: String,
    open val updatedAt: String,
)

data class ResumeSnapshot(
    val snapshotId: String,
    val capturedAt: String,
    override val resumeId: String,
    override val fullName: String,
    override val age: Int,
    override val location: String,
    override val headline: String,
    override val summary: String,
    override val experiences: List<Experience>,
    override val version: Int,
    override val createdAt: String,
    override val updatedAt: String,
) : Resume(resumeId, fullName, age, location, headline, summary, experiences, version, createdAt, updatedAt)

data class TimelineStep(val status: ApplicationStatus, val completed: Boolean, val occurredAt: String?)
data class ApplicationNextStep(val type: String, val title: String, val description: String)

data class CandidateApplication(
    val applicationId: String,
    val jobId: String,
    val status: ApplicationStatus,
    val appliedAt: String,
    val updatedAt: String,
    val version: Int,
    val jobTitle: String,
    val company: Company,
    val matchScore: Int?,
    val scheduledAt: String?,
    val timeline: List<TimelineStep>,
    val resumeSnapshot: ResumeSnapshot,
    val interview: Interview?,
    val nextSteps: List<ApplicationNextStep>,
)

data class Interview(
    val interviewId: String,
    val applicationId: String,
    val scheduledAt: String,
    val timezone: String,
    val durationMinutes: Int,
    val mode: InterviewMode,
    val locationOrMeetingUrl: String?,
    val note: String?,
    val status: InterviewStatus,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class Message(
    val messageId: String,
    val conversationId: String,
    val body: String,
    val senderType: SenderType,
    val sentAt: String,
    val clientMessageId: String?,
    val deliveryStatus: DeliveryStatus,
)

data class LoginRequest(val email: String, val password: String)
data class RefreshTokenRequest(val refreshToken: String)
data class CandidateRegisterRequest(
    val role: UserRole = UserRole.CANDIDATE,
    val fullName: String,
    val email: String,
    val password: String,
    val acceptedTermsVersion: String,
)
data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val refreshExpiresIn: Int,
)
data class AuthUser(
    val userId: String,
    val role: UserRole,
    val fullName: String,
    val email: String,
    val avatarUrl: String?,
    val createdAt: String,
    val updatedAt: String,
    val company: Company?,
)
data class AuthData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val refreshExpiresIn: Int,
    val user: AuthUser,
)
data class SubmitApplicationRequest(val resumeId: String, val contactEmail: String, val shareProfile: Boolean)
data class SendMessageRequest(val body: String, val clientMessageId: String)
data class ReadStateRequest(val lastReadMessageId: String)
data class WithdrawApplicationRequest(val reason: String, val expectedVersion: Int)

data class CandidateStats(val chatCount: Int, val applicationCount: Int, val interviewCount: Int, val savedJobCount: Int)
data class CandidateProfileDto(
    val userId: String, val fullName: String, val email: String, val headline: String, val avatarUrl: String?,
    val location: String, val stats: CandidateStats, val version: Int, val createdAt: String, val updatedAt: String,
)
data class UpdateProfileRequest(val fullName: String? = null, val headline: String? = null,
                                val location: String? = null, val expectedVersion: Int)
data class SaveResumeRequest(
    val fullName: String, val age: Int, val location: String, val headline: String, val summary: String,
    val experiences: List<Experience>, val expectedVersion: Int,
)
