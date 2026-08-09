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

interface CandidateApi {
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
 * Retrofit-backed CandidateApi without changing the screen composables.
 */
object FakeCandidateApi : CandidateApi {
    private val jobs = listOf(
        Job("moonshot", "AI Backend Engineer", "Moonshot AI", "M", "Series B · 500–999", "\$42–68K", listOf("Python", "LLM", "K8s", "RAG"), 96, "Mia Chen", "Hiring Manager"),
        Job("bytelab", "Machine Learning Platform", "ByteDance Seed", "B", "10000+", "\$50–70K", listOf("Intern", "PyTorch", "MLOps"), 91, "Mia Chen", "Hiring Manager"),
        Job("minimax", "Full Stack Engineer, AI Tools", "MiniMax", "M", "No financing needed", "\$30–55K", listOf("React", "Node", "AI Agent"), 84, "Mia Chen", "Hiring Manager"),
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
        val job = jobs.firstOrNull { it.id == jobId } ?: jobs.first()
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
        Conversation("mia", "M", "Mia Chen", "Moonshot AI · We'd like to invite you to interview.", "09:42", 2),
        Conversation("daniel", "D", "Daniel Wu", "ByteLab · Your application has moved to Interview.", "Yesterday", 1),
        Conversation("sophie", "S", "Sophie Lin", "MiniMax · Thanks for applying. We'll review your resume.", "Mon"),
        Conversation("kevin", "K", "Kevin Zhao", "Talent Partner · Could you share your availability this week?", "Aug 3"),
        Conversation("support", "AD", "AD Project Support", "Welcome! Your profile is ready.", "Aug 1"),
    )

    override fun getChatThread(conversationId: String) = ChatThread(
        id = conversationId,
        participantInitial = "M",
        participantName = "Mia Chen",
        participantSubtitle = "Moonshot AI · Online",
        jobId = "moonshot",
        invitationLabel = "INTERVIEW INVITATION",
        jobTitle = "AI Backend Engineer",
        schedule = "Tuesday, Aug 11 · 2:00 PM",
        dayLabel = "Today",
        messages = listOf(
            ChatMessage("Hi Bohao, thanks for applying to the AI Backend Engineer role. We'd like to invite you to a 30-minute interview.", "09:36", false),
            ChatMessage("Would Tuesday, Aug 11 at 2:00 PM work for you? The interview will be held online.", "09:37", false),
            ChatMessage("Thank you! Tuesday at 2:00 PM works for me.", "09:40", true),
            ChatMessage("Great — I’ve sent the meeting details. You can also view them in My Applications.", "09:42", false),
        ),
        status = "✓ Interview scheduled · Tuesday, Aug 11",
    )

    override fun sendMessage(conversationId: String, body: String) = ChatMessage(body, "Now", true)

    override fun getProfile() = CandidateProfile(
        name = "Yan Bohao",
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
            Application("APP-2026-0045", "moonshot", "M", "AI Backend Engineer", "Moonshot AI", "In review", "Applied today · 09:42", 96, listOf("Applied", "In review", "Interview")),
            Application("APP-2026-0038", "bytelab", "B", "ML Platform Intern", "ByteLab", "Interview", "Interview · Aug 8, 14:00", 92, listOf("Applied", "Review", "Interview")),
            Application("APP-2026-0031", "minimax", "M", "Full Stack Engineer, AI Tools", "MiniMax", "Applied", "Applied Aug 2", 84, listOf("Applied", "In review", "Interview")),
        ),
    )

    override fun getApplyConfirmation(jobId: String): ApplyConfirmationData {
        val detail = getJobDetail(jobId)
        return ApplyConfirmationData(
            jobId = detail.job.id,
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
            jobId = detail.job.id,
            companyInitial = detail.job.companyInitial,
            company = detail.job.company,
            jobTitle = detail.job.title,
            jobMeta = "${detail.job.company} · ${detail.location} · ${detail.workplace}",
            status = "Applied",
            submittedAt = "Today · 09:42",
            resumeSnapshot = "Default resume snapshot",
            applicationId = "APP-2026-0045",
            nextSteps = listOf(
                NextStep("Recruiter review", "${detail.job.company} reviews your resume snapshot."),
                NextStep("Status update", "You'll see every stage in My applications."),
                NextStep("Interview invitation", "We'll notify you if an interview is scheduled."),
            ),
        )
    }

    override fun getResume() = ResumeData(
        name = "Yan Bohao",
        age = "27",
        location = "Shanghai",
        headline = "CS Student · AI Backend Engineer",
        summary = "Backend-focused CS student building RAG applications, evaluation pipelines, and distributed service demos.",
        experiences = listOf(
            ResumeExperience(
                "AI Engineering Intern · ByteLab",
                "Implemented FastAPI services, vector-search experiments, and monitoring dashboards with Python.",
            ),
        ),
    )

    override fun saveResume(resume: ResumeData) = resume
}
