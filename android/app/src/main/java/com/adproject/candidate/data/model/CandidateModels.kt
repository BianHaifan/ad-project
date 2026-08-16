package com.adproject.candidate.data.model

import com.adproject.candidate.data.contract.ApplicationListFilter
import com.adproject.candidate.data.contract.ApplicationStatus
import com.adproject.candidate.data.contract.InterviewMode
import com.adproject.candidate.data.contract.ResumeSnapshot
import com.adproject.candidate.data.contract.SenderType
import com.adproject.candidate.data.contract.TimelineStep
import com.adproject.candidate.data.contract.CandidateJobApplicationState

data class Job(
    val jobId: String,
    val title: String,
    val company: String,
    val companyInitial: String,
    val companyMeta: String,
    val salary: String,
    val skills: List<String>,
    val match: Int?,
    val recruiter: RecruiterContact?,
    val companyId: String,
)

data class RecruiterContact(val recruiterId: String, val fullName: String, val title: String)

data class JobFeedData(
    val searchSuggestion: String,
    val jobs: List<Job>,
    val recommendationSource: String? = null,
    val modelVersion: String? = null,
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
    val skills: List<String> = emptyList(),
    val deadline: String? = null,
    val publishedAt: String? = null,
    val matchAnalysisAvailable: Boolean = false,
    val applicationState: CandidateJobApplicationState = CandidateJobApplicationState.NOT_APPLIED,
)

data class LearningData(
    val badge: String,
    val title: String,
    val description: String,
)

data class Conversation(
    val conversationId: String,
    val initial: String,
    val fullName: String,
    val preview: String,
    val lastMessageAt: String,
    val unread: Int = 0,
)

data class ChatMessage(
    val messageId: String,
    val conversationId: String,
    val body: String,
    val senderType: SenderType,
    val sentAt: String,
)

data class ChatThread(
    val conversationId: String,
    val participantInitial: String,
    val participantName: String,
    val participantSubtitle: String,
    val jobId: String,
    val invitationLabel: String,
    val jobTitle: String,
    val scheduledAt: String,
    val mode: InterviewMode,
    val dayLabel: String,
    val messages: List<ChatMessage>,
    val status: String,
)

data class ProfileStat(val value: String, val label: String)

data class ProfileTool(val symbol: String, val label: String, val action: String? = null)

data class ProfileToolGroup(val title: String, val tools: List<ProfileTool>)

data class CandidateProfile(
    val fullName: String,
    val headline: String,
    val stats: List<ProfileStat>,
    val toolGroups: List<ProfileToolGroup>,
)

data class Application(
    val applicationId: String,
    val jobId: String,
    val initial: String,
    val title: String,
    val company: String,
    val status: ApplicationStatus,
    val appliedAt: String,
    val scheduledAt: String?,
    val match: Int,
    val timeline: List<TimelineStep>,
)

data class ApplicationsData(
    val activeCount: Int,
    val interviewCount: Int,
    val archivedCount: Int,
    val applications: List<Application>,
    val selectedFilter: ApplicationListFilter = ApplicationListFilter.ACTIVE,
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
    val resumeSnapshot: ResumeSnapshot,
    val applicationId: String,
    val nextSteps: List<NextStep>,
)

data class ResumeExperience(
    val experienceId: String?,
    val title: String,
    val company: String,
    val description: String,
    val startDate: String,
    val endDate: String?,
)

data class ResumeData(
    val resumeId: String,
    val fullName: String,
    val age: Int,
    val location: String,
    val headline: String,
    val summary: String,
    val experiences: List<ResumeExperience>,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val skills: List<String> = emptyList(),
)
