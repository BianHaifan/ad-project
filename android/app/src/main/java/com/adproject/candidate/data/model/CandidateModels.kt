package com.adproject.candidate.data.model

data class SignInDefaults(val email: String, val password: String)

data class RegistrationDefaults(
    val fullName: String,
    val email: String,
    val password: String,
    val agreed: Boolean,
)

data class Job(
    val id: String,
    val title: String,
    val company: String,
    val companyInitial: String,
    val companyMeta: String,
    val salary: String,
    val skills: List<String>,
    val match: Int,
    val recruiterName: String,
    val recruiterRole: String,
)

data class JobFeedData(
    val searchSuggestion: String,
    val jobs: List<Job>,
)

data class JobDetailData(
    val job: Job,
    val location: String,
    val employmentType: String,
    val workplace: String,
    val strongMatches: String,
    val gap: String,
    val description: String,
    val requirements: String,
)

data class LearningData(
    val badge: String,
    val title: String,
    val description: String,
)

data class Conversation(
    val id: String,
    val initial: String,
    val name: String,
    val preview: String,
    val time: String,
    val unread: Int = 0,
)

data class ChatMessage(val body: String, val time: String, val sent: Boolean)

data class ChatThread(
    val id: String,
    val participantInitial: String,
    val participantName: String,
    val participantSubtitle: String,
    val jobId: String,
    val invitationLabel: String,
    val jobTitle: String,
    val schedule: String,
    val dayLabel: String,
    val messages: List<ChatMessage>,
    val status: String,
)

data class ProfileStat(val value: String, val label: String)

data class ProfileTool(val symbol: String, val label: String, val action: String? = null)

data class ProfileToolGroup(val title: String, val tools: List<ProfileTool>)

data class CandidateProfile(
    val name: String,
    val headline: String,
    val stats: List<ProfileStat>,
    val toolGroups: List<ProfileToolGroup>,
)

data class Application(
    val id: String,
    val jobId: String,
    val initial: String,
    val title: String,
    val company: String,
    val status: String,
    val timing: String,
    val match: Int,
    val steps: List<String>,
)

data class ApplicationsData(
    val activeCount: Int,
    val interviewCount: Int,
    val archivedCount: Int,
    val applications: List<Application>,
)

data class ApplyConfirmationData(
    val jobId: String,
    val companyInitial: String,
    val company: String,
    val companyMeta: String,
    val jobTitle: String,
    val salaryAndLocation: String,
    val resumeName: String,
    val resumeMeta: String,
    val resumeStatus: String,
    val contactEmail: String,
    val visibleInformation: String,
)

data class NextStep(val title: String, val description: String)

data class SubmissionData(
    val jobId: String,
    val companyInitial: String,
    val company: String,
    val jobTitle: String,
    val jobMeta: String,
    val status: String,
    val submittedAt: String,
    val resumeSnapshot: String,
    val applicationId: String,
    val nextSteps: List<NextStep>,
)

data class ResumeExperience(val title: String, val description: String)

data class ResumeData(
    val name: String,
    val age: String,
    val location: String,
    val headline: String,
    val summary: String,
    val experiences: List<ResumeExperience>,
)
