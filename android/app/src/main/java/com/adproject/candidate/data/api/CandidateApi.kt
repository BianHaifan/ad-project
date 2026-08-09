package com.adproject.candidate.data.api

import com.adproject.candidate.data.model.Application
import com.adproject.candidate.data.model.ApplicationsData
import com.adproject.candidate.data.model.ApplyConfirmationData
import com.adproject.candidate.data.model.CandidateProfile
import com.adproject.candidate.data.model.ChatMessage
import com.adproject.candidate.data.model.ChatThread
import com.adproject.candidate.data.model.Conversation
import com.adproject.candidate.data.model.Job
import com.adproject.candidate.data.model.JobDetailData
import com.adproject.candidate.data.model.JobFeedData
import com.adproject.candidate.data.model.LearningData
import com.adproject.candidate.data.model.NextStep
import com.adproject.candidate.data.model.ProfileStat
import com.adproject.candidate.data.model.ProfileTool
import com.adproject.candidate.data.model.ProfileToolGroup
import com.adproject.candidate.data.model.RegistrationDefaults
import com.adproject.candidate.data.model.ResumeData
import com.adproject.candidate.data.model.ResumeExperience
import com.adproject.candidate.data.model.SignInDefaults
import com.adproject.candidate.data.model.SubmissionData
import com.adproject.candidate.data.contract.ApplicationStatus
import com.adproject.candidate.data.contract.Experience
import com.adproject.candidate.data.contract.ResumeSnapshot
import com.adproject.candidate.data.contract.SenderType
import com.adproject.candidate.data.contract.TimelineStep
import com.adproject.candidate.data.model.RecruiterContact

interface CandidateRepository {
    fun getSignInDefaults(): SignInDefaults
    fun getRegistrationDefaults(): RegistrationDefaults
    fun getJobFeed(): JobFeedData
    fun getJobDetail(jobId: String): JobDetailData
    fun getLearning(): LearningData
    fun getConversations(): List<Conversation>
    fun getChatThread(conversationId: String): ChatThread
    fun sendMessage(conversationId: String, body: String): ChatMessage
    fun getProfile(): CandidateProfile
    fun getApplications(): ApplicationsData
    fun getApplyConfirmation(jobId: String): ApplyConfirmationData
    fun submitApplication(jobId: String): SubmissionData
    fun getResume(): ResumeData
    fun saveResume(resume: ResumeData): ResumeData
}

/**
 * The only source of frontend demo data. Replace this implementation with a
 * Retrofit-backed CandidateRepository without changing the screen composables.
 */
object FakeCandidateRepository : CandidateRepository {
    private const val NOW = "2026-08-09T01:42:00Z"
    private val recruiter = RecruiterContact("rec_001", "Mia Chen", "Hiring Manager")
    private val jobs = listOf(
        Job("moonshot", "AI Backend Engineer", "Moonshot AI", "M", "Series B · 500–999", "SGD 5–8K", listOf("Python", "LLM", "K8s", "RAG"), 96, recruiter),
        Job("bytelab", "Machine Learning Platform", "ByteDance Seed", "B", "10000+", "SGD 6–9K", listOf("Intern", "PyTorch", "MLOps"), 91, recruiter),
        Job("minimax", "Full Stack Engineer, AI Tools", "MiniMax", "M", "No financing needed", "SGD 5–7K", listOf("React", "Node", "AI Agent"), 84, recruiter),
    )

    override fun getSignInDefaults() = SignInDefaults("bohao.yan@example.com", "password123")

    override fun getRegistrationDefaults() = RegistrationDefaults(
        fullName = "Yan Bohao",
        email = "bohao.yan@example.com",
        password = "password123",
        agreed = true,
    )

    override fun getJobFeed() = JobFeedData(
        searchSuggestion = "AI Engineer, Backend, LLM",
        jobs = jobs,
    )

    override fun getJobDetail(jobId: String): JobDetailData {
        val job = jobs.firstOrNull { it.jobId == jobId } ?: jobs.first()
        return JobDetailData(
            job = job,
            location = "Shanghai",
            employmentType = "Full-time",
            workplace = "Hybrid",
            strongMatches = "Python, LLM / RAG, FastAPI",
            gap = "latency optimization evidence",
            description = "Build production AI services for LLM and RAG products. Own APIs, evaluation, observability, and scalable deployment.",
            requirements = "2+ years  ·  Python / FastAPI  ·  Kubernetes",
        )
    }

    override fun getLearning() = LearningData(
        badge = "COMING SOON",
        title = "Learning is not available yet",
        description = "We're preparing personalized learning paths. Continue with Jobs, Messages, or Me below.",
    )

    override fun getConversations() = listOf(
        Conversation("mia", "M", "Mia Chen", "Moonshot AI · We'd like to invite you to interview.", NOW, 2),
        Conversation("daniel", "D", "Daniel Wu", "ByteLab · Your application has moved to Interview.", "2026-08-08T02:00:00Z", 1),
        Conversation("sophie", "S", "Sophie Lin", "MiniMax · Thanks for applying. We'll review your resume.", "2026-08-04T02:00:00Z"),
        Conversation("kevin", "K", "Kevin Zhao", "Talent Partner · Could you share your availability this week?", "2026-08-03T02:00:00Z"),
        Conversation("support", "AD", "AD Project Support", "Welcome! Your profile is ready.", "2026-08-01T02:00:00Z"),
    )

    override fun getChatThread(conversationId: String) = ChatThread(
        conversationId = conversationId,
        participantInitial = "M",
        participantName = "Mia Chen",
        participantSubtitle = "Moonshot AI · Online",
        jobId = "moonshot",
        invitationLabel = "INTERVIEW INVITATION",
        jobTitle = "AI Backend Engineer",
        scheduledAt = "2026-08-11T06:00:00Z",
        mode = com.adproject.candidate.data.contract.InterviewMode.ONLINE,
        dayLabel = "Today",
        messages = listOf(
            ChatMessage("msg_001", conversationId, "Hi Bohao, thanks for applying to the AI Backend Engineer role. We'd like to invite you to a 30-minute interview.", SenderType.RECRUITER, "2026-08-09T01:36:00Z"),
            ChatMessage("msg_002", conversationId, "Would Tuesday, Aug 11 at 2:00 PM work for you? The interview will be held online.", SenderType.RECRUITER, "2026-08-09T01:37:00Z"),
            ChatMessage("msg_003", conversationId, "Thank you! Tuesday at 2:00 PM works for me.", SenderType.CANDIDATE, "2026-08-09T01:40:00Z"),
            ChatMessage("msg_004", conversationId, "Great — I’ve sent the meeting details. You can also view them in My Applications.", SenderType.RECRUITER, NOW),
        ),
        status = "✓ Interview scheduled · Tuesday, Aug 11",
    )

    override fun sendMessage(conversationId: String, body: String) =
        ChatMessage("msg_local", conversationId, body, SenderType.CANDIDATE, NOW)

    override fun getProfile() = CandidateProfile(
        fullName = "Yan Bohao",
        headline = "Online Resume - CS Student",
        stats = listOf(
            ProfileStat("14", "Chats"),
            ProfileStat("28", "Applied"),
            ProfileStat("6", "Interviews"),
            ProfileStat("31", "Saved"),
        ),
        toolGroups = listOf(
            ProfileToolGroup("Common Tools", listOf(
                ProfileTool("R", "Online\nResume", "resume"),
                ProfileTool("U", "Resume\nUpload"),
                ProfileTool("G", "Career Goal"),
                ProfileTool("A", "Job Agent"),
            )),
            ProfileToolGroup("Other Tools", listOf(
                ProfileTool("S", "Resume\nRating"),
                ProfileTool("L", "Learning\nPath"),
                ProfileTool("M", "Company\nMatch"),
                ProfileTool("C", "Community"),
                ProfileTool("I", "Mock Prep"),
                ProfileTool("H", "Support"),
            )),
        ),
    )

    override fun getApplications() = ApplicationsData(
        activeCount = 3,
        interviewCount = 1,
        archivedCount = 8,
        applications = listOf(
            Application("APP-2026-0045", "moonshot", "M", "AI Backend Engineer", "Moonshot AI", ApplicationStatus.IN_REVIEW, NOW, null, 96, timeline(ApplicationStatus.IN_REVIEW, NOW)),
            Application("APP-2026-0038", "bytelab", "B", "ML Platform Intern", "ByteLab", ApplicationStatus.INTERVIEW, "2026-08-02T07:00:00Z", "2026-08-11T06:00:00Z", 92, timeline(ApplicationStatus.INTERVIEW, "2026-08-02T07:00:00Z")),
            Application("APP-2026-0031", "minimax", "M", "Full Stack Engineer, AI Tools", "MiniMax", ApplicationStatus.APPLIED, "2026-08-02T07:00:00Z", null, 84, timeline(ApplicationStatus.APPLIED, "2026-08-02T07:00:00Z")),
        ),
    )

    override fun getApplyConfirmation(jobId: String): ApplyConfirmationData {
        val detail = getJobDetail(jobId)
        return ApplyConfirmationData(
            jobId = detail.job.jobId,
            companyInitial = detail.job.companyInitial,
            company = detail.job.company,
            companyMeta = "${detail.job.companyMeta} employees",
            jobTitle = detail.job.title,
            salaryAndLocation = "${detail.job.salary}  ·  ${detail.location}  ·  ${detail.workplace}",
            resumeName = "Yan Bohao · CS Student",
            resumeMeta = "Default · Updated today",
            resumeStatus = "Ready",
            contactEmail = "bohao.yan@example.com",
            visibleInformation = "Resume + profile",
        )
    }

    override fun submitApplication(jobId: String): SubmissionData {
        val detail = getJobDetail(jobId)
        return SubmissionData(
            jobId = detail.job.jobId,
            companyInitial = detail.job.companyInitial,
            company = detail.job.company,
            jobTitle = detail.job.title,
            jobMeta = "${detail.job.company} · ${detail.location} · ${detail.workplace}",
            status = "Applied",
            submittedAt = "Today · 09:42",
            resumeSnapshot = resumeSnapshot(),
            applicationId = "APP-2026-0045",
            nextSteps = listOf(
                NextStep("Recruiter review", "${detail.job.company} reviews your resume snapshot."),
                NextStep("Status update", "You'll see every stage in My applications."),
                NextStep("Interview invitation", "We'll notify you if an interview is scheduled."),
            ),
        )
    }

    override fun getResume() = ResumeData(
        resumeId = "resume_001",
        fullName = "Yan Bohao",
        age = 27,
        location = "Shanghai",
        headline = "CS Student · AI Backend Engineer",
        summary = "Backend-focused CS student building RAG applications, evaluation pipelines, and distributed service demos.",
        experiences = listOf(
            ResumeExperience(
                experienceId = "exp_001",
                title = "AI Engineering Intern",
                company = "ByteLab",
                description = "Implemented FastAPI services, vector-search experiments, and monitoring dashboards with Python.",
                startDate = "2025-06",
                endDate = "2025-12",
            ),
        ),
        version = 3,
        createdAt = "2026-01-10T02:00:00Z",
        updatedAt = NOW,
    )

    override fun saveResume(resume: ResumeData) = resume

    private fun timeline(current: ApplicationStatus, appliedAt: String): List<TimelineStep> {
        val order = listOf(ApplicationStatus.APPLIED, ApplicationStatus.IN_REVIEW, ApplicationStatus.INTERVIEW)
        val currentIndex = order.indexOf(current).coerceAtLeast(0)
        return order.mapIndexed { index, status ->
            TimelineStep(status, index <= currentIndex, if (index == 0) appliedAt else if (index <= currentIndex) NOW else null)
        }
    }

    private fun resumeSnapshot() = ResumeSnapshot(
        snapshotId = "snapshot_001",
        capturedAt = NOW,
        resumeId = "resume_001",
        fullName = "Yan Bohao",
        age = 27,
        location = "Shanghai",
        headline = "CS Student · AI Backend Engineer",
        summary = "Backend-focused CS student building RAG applications.",
        experiences = listOf(Experience("exp_001", "AI Engineering Intern", "ByteLab", "Built FastAPI services.", "2025-06", "2025-12")),
        version = 3,
        createdAt = "2026-01-10T02:00:00Z",
        updatedAt = NOW,
    )
}
