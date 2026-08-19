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
    const val JOB_PREFERENCES = "/candidate/job-preferences"
    const val JOB_RECOMMENDATIONS = "/candidate/recommendations/jobs"
    const val LEARNING = "/features/learning"
    const val RECRUITERS = "/candidate/recruiters"
    const val COMPANIES = "/candidate/companies"

    fun job(jobId: String) = "/jobs/$jobId"
    fun submitApplication(jobId: String) = "/jobs/$jobId/applications"
    fun application(applicationId: String) = "$APPLICATIONS/$applicationId"
    fun withdrawApplication(applicationId: String) = "$APPLICATIONS/$applicationId/withdraw"
    fun conversation(conversationId: String) = "$CONVERSATIONS/$conversationId"
    fun messages(conversationId: String) = "${conversation(conversationId)}/messages"
    fun readState(conversationId: String) = "${conversation(conversationId)}/read-state"
    fun recruiterPublicProfile(recruiterId: String) = "$RECRUITERS/$recruiterId"
    fun companyPublicProfile(companyId: String) = "$COMPANIES/$companyId"
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
enum class Gender { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }
enum class ApplicationStatus { APPLIED, IN_REVIEW, INTERVIEW, OFFERED, REJECTED, WITHDRAWN }
enum class CandidateJobApplicationState { NOT_APPLIED, APPLIED, IN_REVIEW, INTERVIEW, OFFERED, REJECTED, WITHDRAWN }
enum class ApplicationListFilter { ACTIVE, INTERVIEW, ARCHIVED }
enum class JobStatus { DRAFT, ACTIVE, PAUSED, CLOSED }
enum class InterviewStatus { SCHEDULED, COMPLETED, CANCELLED }
enum class InterviewMode { ONLINE, ONSITE, PHONE }
enum class MeetingProvider { MANUAL, GOOGLE_MEET }
enum class MeetingSyncStatus { NOT_APPLICABLE, PENDING, READY, FAILED }
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

data class PublicCompanySummary(
    val companyId: String,
    val name: String,
    val logoUrl: String?,
    val verificationStatus: String?,
)

data class RecruiterPublicProfile(
    val recruiterId: String,
    val fullName: String,
    val avatarUrl: String?,
    val title: String,
    val bio: String?,
    val company: PublicCompanySummary,
)

data class CompanyPublicProfile(
    val companyId: String,
    val name: String,
    val logoUrl: String?,
    val description: String?,
    val location: String?,
    val verificationStatus: String?,
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
    val isSaved: Boolean? = null,
)

data class CandidateJobDetail(
    val job: CandidateJob,
    val matchAnalysis: MatchAnalysis?,
    val applicationState: CandidateJobApplicationState,
    val isSaved: Boolean,
)

data class MatchAnalysis(
    val score: Int? = null,
    val evidence: List<String> = emptyList(),
    val strongMatches: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    val modelVersion: String? = null,
    val generatedAt: String? = null,
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
    open val skills: List<String> = emptyList(),
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
    override val skills: List<String> = emptyList(),
) : Resume(resumeId, fullName, age, location, headline, summary, experiences, version, createdAt, updatedAt, skills)

data class TimelineStep(val status: ApplicationStatus, val completed: Boolean, val occurredAt: String?)
data class ApplicationNextStep(val type: String, val title: String, val description: String)

data class CandidateApplicationSummary(
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
)

data class ApplicationCounts(val active: Int, val interview: Int, val archived: Int)
data class CandidateApplicationListMeta(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val hasNext: Boolean,
    val counts: ApplicationCounts,
)
data class CandidateApplicationPage(
    val applications: List<CandidateApplicationSummary>,
    val meta: CandidateApplicationListMeta,
)

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
    // Meeting sync fields are candidate-safe only. Older backends that do not
    // send them are treated as a plain manual interview with no external sync.
    val meetingProvider: MeetingProvider = MeetingProvider.MANUAL,
    val meetingSyncStatus: MeetingSyncStatus = MeetingSyncStatus.NOT_APPLICABLE,
)

data class Message(
    val messageId: String,
    val conversationId: String,
    val body: String,
    val senderType: SenderType,
    val sentAt: String,
    val clientMessageId: String?,
    val deliveryStatus: DeliveryStatus,
    val attachment: MessageAttachment? = null,
)

data class MessageAttachment(
    val attachmentId: String,
    val fileName: String,
    val sizeBytes: Long,
    val contentType: String,
)

data class ConversationParticipant(
    val userId: String,
    val fullName: String,
    val avatarUrl: String?,
    val title: String?,
    val company: Company?,
    val online: Boolean,
)

data class ConversationSummary(
    val conversationId: String,
    val applicationId: String,
    val jobId: String,
    val createdAt: String,
    val updatedAt: String,
    val participant: ConversationParticipant,
    val lastMessage: Message?,
    val unreadCount: Int,
    val jobTitle: String,
)

data class ConversationDetail(
    val conversationId: String,
    val applicationId: String,
    val jobId: String,
    val createdAt: String,
    val updatedAt: String,
    val participant: ConversationParticipant,
    val context: InterviewContext?,
)

data class InterviewContext(
    val type: String,
    val interviewId: String,
    val applicationId: String,
    val jobId: String,
    val jobTitle: String,
    val scheduledAt: String,
    val mode: InterviewMode,
    val timezone: String,
    val durationMinutes: Int,
    val locationOrMeetingUrl: String?,
    val status: InterviewStatus,
)

data class LoginRequest(val email: String, val password: String)
data class RefreshTokenRequest(val refreshToken: String)
data class PasswordResetRequest(val email: String)
data class PasswordResetConfirmRequest(val email: String, val code: String, val newPassword: String)
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
    val onboardingRequired: Boolean? = null,
)
data class CandidateOnboardingRequest(
    val headline: String,
    val location: String,
    val age: Int,
    val resumeSummary: String,
    val skills: List<String>,
    val desiredTitle: String,
    val preferredLocation: String,
    val workplaceType: WorkplaceType,
    val employmentType: EmploymentType,
)
data class SubmitApplicationRequest(val resumeId: String, val contactEmail: String, val shareProfile: Boolean)
data class SendMessageRequest(val body: String, val clientMessageId: String)
data class ReadStateRequest(val lastReadMessageId: String)
data class WithdrawApplicationRequest(val reason: String, val expectedVersion: Int)

data class CandidateStats(val chatCount: Int, val applicationCount: Int, val interviewCount: Int, val savedJobCount: Int)
data class CandidateProfileDto(
    val userId: String, val fullName: String, val email: String, val headline: String, val avatarUrl: String?,
    val location: String, val age: Int? = null, val gender: Gender? = null, val phone: String? = null, val birthplace: String? = null,
    val stats: CandidateStats, val version: Int, val createdAt: String, val updatedAt: String,
)
// fullName/location are required and always sent non-null; age/gender/phone/birthplace are
// nullable and Moshi serializes a null value, which the backend treats as "clear this field".
data class UpdateProfileRequest(
    val fullName: String? = null,
    val headline: String? = null,
    val location: String? = null,
    val age: Int? = null,
    val gender: Gender? = null,
    val phone: String? = null,
    val birthplace: String? = null,
    val expectedVersion: Int,
)
data class AvatarMetadata(val userId: String, val avatarUrl: String?, val contentType: String?,
                          val sizeBytes: Long, val updatedAt: String?)
data class SaveResumeRequest(
    val summary: String,
    val experiences: List<Experience>,
    val expectedVersion: Int,
    val skills: List<String> = emptyList(),
)

data class JobPreference(
    val desiredTitles: List<String>,
    val preferredLocations: List<String>,
    val workplaceTypes: List<WorkplaceType>,
    val employmentTypes: List<EmploymentType>,
    val minimumSalary: Long?,
    val salaryCurrency: String,
    val salaryPeriod: String,
    val version: Int,
    val createdAt: String?,
    val updatedAt: String?,
)

data class SaveJobPreferenceRequest(
    val desiredTitles: List<String>,
    val preferredLocations: List<String>,
    val workplaceTypes: List<WorkplaceType>,
    val employmentTypes: List<EmploymentType>,
    val minimumSalary: Long?,
    val salaryCurrency: String = "SGD",
    val salaryPeriod: String = "MONTH",
    val expectedVersion: Int,
)

data class RecommendedJob(
    val jobId: String,
    val title: String,
    val companyId: String,
    val companyName: String,
    val location: String,
    val employmentType: EmploymentType,
    val workplaceType: WorkplaceType,
    val salaryMin: Int,
    val salaryMax: Int,
    val salaryCurrency: String,
    val salaryPeriod: String,
    val description: String,
    val skills: List<String>,
    val matchScore: Int,
    val rank: Int,
    val matchAnalysis: MatchAnalysis,
    val isSaved: Boolean? = null,
    val recruiter: RecruiterContact? = null,
)

data class RecommendationMeta(
    val source: String,
    val modelVersion: String,
    val featureVersion: String,
    val modelStatus: String,
    val inferenceMs: Int,
    val generatedAt: String,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val hasNext: Boolean,
)

data class RecommendationEnvelope(
    val data: List<RecommendedJob>,
    val meta: RecommendationMeta,
)

data class CreateAgentRunRequest(
    val instruction: String,
    val conversationId: String? = null,
)
data class ConfirmAgentRunRequest(val confirmationId: String, val expectedRunVersion: Int)
data class AgentTarget(val id: String)
data class AgentFieldChange(val field: String, val oldValue: Any?, val newValue: Any?)
data class AgentPreview(
    val confirmationId: String,
    val targetType: String,
    val targetId: String,
    val expectedVersion: Int,
    val expiresAt: String,
    val changes: List<AgentFieldChange>,
)
data class AgentStep(
    val sequence: Int,
    val type: String,
    val tool: String?,
    val status: String,
    val errorCode: String?,
    val createdAt: String,
)
data class AgentExecutionResult(
    val operation: String,
    val targetType: String,
    val targetId: String,
    val previousVersion: Int,
    val newVersion: Int,
    val completedAt: String,
    val appliedChanges: List<AgentFieldChange>,
    val queryResult: AgentQueryResult? = null,
)
data class AgentQueryResult(
    val section: String,
    val summary: String? = null,
    val skills: List<String>? = null,
    val experiences: List<Experience>? = null,
)
data class AgentRun(
    val runId: String,
    val conversationId: String,
    val instruction: String,
    val status: String,
    val confirmationStatus: String,
    val target: AgentTarget?,
    val steps: List<AgentStep>,
    val preview: AgentPreview?,
    val result: AgentExecutionResult?,
    val message: String?,
    val errorCode: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
)
data class AgentConversation(val conversationId: String?, val runs: List<AgentRun>)
data class AgentConversationSummary(
    val conversationId: String,
    val lastInstruction: String,
    val lastMessage: String?,
    val updatedAt: String,
)
