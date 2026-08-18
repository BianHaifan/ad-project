package com.adproject.candidate.feature.applications

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.data.contract.ApplicationCounts
import com.adproject.candidate.data.contract.ApplicationListFilter
import com.adproject.candidate.data.contract.ApplicationNextStep
import com.adproject.candidate.data.contract.ApplicationStatus
import com.adproject.candidate.data.contract.CandidateApplication
import com.adproject.candidate.data.contract.CandidateApplicationSummary
import com.adproject.candidate.data.contract.CandidateJob
import com.adproject.candidate.data.contract.CandidateJobApplicationState
import com.adproject.candidate.data.contract.CandidateJobDetail
import com.adproject.candidate.data.contract.CandidateProfileDto
import com.adproject.candidate.data.contract.CandidateStats
import com.adproject.candidate.data.contract.Company
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.Experience
import com.adproject.candidate.data.contract.Gender
import com.adproject.candidate.data.contract.Interview
import com.adproject.candidate.data.contract.InterviewMode
import com.adproject.candidate.data.contract.InterviewStatus
import com.adproject.candidate.data.contract.JobStatus
import com.adproject.candidate.data.contract.MeetingProvider
import com.adproject.candidate.data.contract.MeetingSyncStatus
import com.adproject.candidate.data.contract.Resume
import com.adproject.candidate.data.contract.ResumeSnapshot
import com.adproject.candidate.data.contract.Salary
import com.adproject.candidate.data.contract.TimelineStep
import com.adproject.candidate.data.contract.Visibility
import com.adproject.candidate.data.contract.WorkplaceType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ApplicationsScreensUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun hasToggleableState() = SemanticsMatcher("hasToggleableState") {
        it.config.contains(SemanticsProperties.ToggleableState)
    }

    private fun company(name: String = "Real Company") = Company(
        companyId = "c1", name = name, logoUrl = null, stage = "Series A", employeeRange = "51-200",
        verificationStatus = "APPROVED", website = null, description = null, location = "Singapore",
        version = 1, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun summary(
        applicationId: String = "app-1",
        status: ApplicationStatus = ApplicationStatus.IN_REVIEW,
        match: Int? = 90,
        scheduledAt: String? = null,
        jobTitle: String = "Backend Engineer",
        companyName: String = "Real Company",
    ) = CandidateApplicationSummary(
        applicationId = applicationId, jobId = "job-1", status = status,
        appliedAt = "2026-08-01T09:00:00+08:00", updatedAt = "2026-08-11T10:30:00+08:00", version = 3,
        jobTitle = jobTitle, company = company(name = companyName), matchScore = match, scheduledAt = scheduledAt,
        timeline = listOf(
            TimelineStep(ApplicationStatus.APPLIED, true, "2026-08-01T09:00:00+08:00"),
            TimelineStep(ApplicationStatus.IN_REVIEW, false, null),
        ),
    )

    private fun interview(
        mode: InterviewMode = InterviewMode.ONLINE,
        status: InterviewStatus = InterviewStatus.SCHEDULED,
        location: String? = "https://meet.google.com/abc",
        provider: MeetingProvider = MeetingProvider.MANUAL,
        sync: MeetingSyncStatus = MeetingSyncStatus.NOT_APPLICABLE,
    ) = Interview(
        interviewId = "i1", applicationId = "app-1", scheduledAt = "2026-08-20T14:00:00+08:00",
        timezone = "GMT+8", durationMinutes = 45, mode = mode, locationOrMeetingUrl = location,
        note = null, status = status, version = 1, createdAt = "2026-08-10T00:00:00Z",
        updatedAt = "2026-08-10T00:00:00Z", meetingProvider = provider, meetingSyncStatus = sync,
    )

    private fun application(status: ApplicationStatus = ApplicationStatus.APPLIED) = CandidateApplication(
        applicationId = "app-1", jobId = "job-1", status = status,
        appliedAt = "2026-08-01T09:00:00+08:00", updatedAt = "2026-08-11T10:30:00+08:00", version = 3,
        jobTitle = "Backend Engineer", company = company(), matchScore = 88,
        scheduledAt = null, timeline = listOf(
            TimelineStep(ApplicationStatus.APPLIED, true, "2026-08-01T09:00:00+08:00"),
            TimelineStep(ApplicationStatus.IN_REVIEW, true, "2026-08-05T10:00:00+08:00"),
        ),
        resumeSnapshot = ResumeSnapshot(
            snapshotId = "snap-1", capturedAt = "2026-08-01T09:05:00+08:00", resumeId = "resume-1",
            fullName = "Alice Tan", age = 28, location = "Singapore", headline = "Backend Developer",
            summary = "5 years of backend work.", version = 5, createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            experiences = listOf(Experience("e1", "Senior Engineer", "Alpha Co", "Built APIs", "2022-01", "2026-01")),
        ),
        interview = interview(),
        nextSteps = listOf(ApplicationNextStep("REVIEW", "Application review", "Recruiter will review your details.")),
    )

    private fun jobDetail(state: CandidateJobApplicationState = CandidateJobApplicationState.NOT_APPLIED) =
        CandidateJobDetail(
            job = CandidateJob(
                jobId = "job-1", title = "Backend Engineer", company = company(),
                employmentType = EmploymentType.FULL_TIME, workplaceType = WorkplaceType.HYBRID,
                location = "Singapore", salary = Salary(5000, 8000, "SGD", "month"),
                description = "Description", requirements = listOf("Python"), skills = listOf("Python"),
                deadline = null, visibility = Visibility.PUBLIC, status = JobStatus.ACTIVE,
                publishedAt = null, version = 2, createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z", matchScore = 90, recruiter = null,
            ),
            matchAnalysis = null, applicationState = state, isSaved = false,
        )

    private fun profile() = CandidateProfileDto(
        userId = "u1", fullName = "Alice Tan", email = "alice@example.com", headline = "Backend Developer",
        avatarUrl = null, location = "Singapore", age = 28, gender = Gender.FEMALE, phone = null,
        birthplace = null, stats = CandidateStats(1, 2, 0, 3), version = 1,
        createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun resume() = Resume(
        resumeId = "resume-1", fullName = "Alice Tan", age = 28, location = "Singapore",
        headline = "Backend Developer", summary = "Summary", experiences = emptyList(),
        version = 5, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun confirmationData() = com.adproject.candidate.data.model.ApplyConfirmationData(
        jobId = "job-1", companyInitial = "R", company = "Real Company", companyMeta = "Series A · 51-200",
        jobTitle = "Backend Engineer", salaryAndLocation = "SGD 5000–8000/month",
        resumeName = "Alice Tan.pdf", resumeMeta = "Updated Aug 1, 2026", resumeStatus = "Ready",
        contactEmail = "alice@example.com", visibleInformation = "Full profile",
    )

    private fun submissionData() = com.adproject.candidate.data.model.SubmissionData(
        jobId = "job-1", companyInitial = "R", company = "Real Company",
        jobTitle = "Backend Engineer", jobMeta = "Series A · 51-200", status = "Applied",
        submittedAt = "Aug 1, 2026",
        resumeSnapshot = ResumeSnapshot(
            snapshotId = "snap-1", capturedAt = "2026-08-01T09:05:00+08:00", resumeId = "resume-1",
            fullName = "Alice Tan", age = 28, location = "Singapore", headline = "Backend Developer",
            summary = "Summary", version = 5, createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z", experiences = emptyList(),
        ),
        applicationId = "app-1",
        nextSteps = listOf(com.adproject.candidate.data.model.NextStep("Application review", "Recruiter will review.")),
    )

    // ---- Legacy screens (ApplicationScreens.kt) ----

    @Test
    fun applyConfirmationDataScreenRendersAndActs() {
        var backed = 0
        var submitted = 0
        composeRule.setContent {
            ApplyConfirmationScreen(confirmationData(), onBack = { backed++ }, onSubmit = { submitted++ })
        }
        composeRule.onNodeWithText("Confirm application").assertIsDisplayed()
        composeRule.onNodeWithText("Backend Engineer").assertExists()
        composeRule.onNodeWithText("Alice Tan.pdf").assertExists()
        composeRule.onNodeWithText("Contact email").assertExists()
        composeRule.onNodeWithText("alice@example.com").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, backed)
        composeRule.onNodeWithText("Submit application").performClick()
        assertEquals(1, submitted)
    }

    @Test
    fun applicationSubmittedDataScreenRendersAndActs() {
        var applications = 0
        var jobs = 0
        composeRule.setContent {
            ApplicationSubmittedScreen(submissionData(), onApplications = { applications++ }, onJobs = { jobs++ })
        }
        composeRule.onNodeWithText("Application submitted").assertIsDisplayed()
        composeRule.onNodeWithText("Real Company has received your application.").assertExists()
        composeRule.onNodeWithText("Application review").assertExists()
        composeRule.onNodeWithText("View my applications").performScrollTo().performClick()
        assertEquals(1, applications)
        composeRule.onNodeWithText("Back to jobs").performScrollTo().performClick()
        assertEquals(1, jobs)
    }

    // ---- RealApplyConfirmationScreen ----

    @Test
    fun applyConfirmationLoading() {
        composeRule.setContent {
            RealApplyConfirmationScreen(ApplicationFlowUiState(loading = true), {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Confirm application").assertIsDisplayed()
    }

    @Test
    fun applyConfirmationResumeMissing() {
        var created = 0
        var retried = 0
        composeRule.setContent {
            RealApplyConfirmationScreen(
                ApplicationFlowUiState(loading = false, resumeMissing = true),
                onBack = {}, onRetry = { retried++ }, onCreateResume = { created++ },
                onShareProfile = {}, onSubmit = {},
            )
        }
        composeRule.onNodeWithText("Create your default resume before applying.").assertIsDisplayed()
        composeRule.onNodeWithText("Create resume").performClick()
        assertEquals(1, created)
        composeRule.onNodeWithText("I created it · reload").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun applyConfirmationUnavailableShowsMessage() {
        var retried = 0
        composeRule.setContent {
            RealApplyConfirmationScreen(
                ApplicationFlowUiState(loading = false, message = "Unable to prepare this application."),
                onBack = {}, onRetry = { retried++ }, onCreateResume = {}, onShareProfile = {}, onSubmit = {},
            )
        }
        composeRule.onNodeWithText("Unable to prepare this application.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun applyConfirmationContentSubmitsAndBacks() {
        var submitted = 0
        var backed = 0
        var shared: Boolean? = null
        composeRule.setContent {
            RealApplyConfirmationScreen(
                ApplicationFlowUiState(
                    loading = false, jobId = "job-1", job = jobDetail(), profile = profile(),
                    resume = resume(), shareProfile = true,
                ),
                onBack = { backed++ }, onRetry = {}, onCreateResume = {},
                onShareProfile = { shared = it }, onSubmit = { submitted++ },
            )
        }
        composeRule.onNodeWithText("Real Company").assertExists()
        composeRule.onNodeWithText("Backend Engineer").assertExists()
        composeRule.onNodeWithText("Default resume").assertExists()
        composeRule.onNodeWithText("Alice Tan").assertExists()
        composeRule.onNodeWithText("Contact email: alice@example.com").assertExists()
        composeRule.onNodeWithText("Submit application").performScrollTo().performClick()
        assertEquals(1, submitted)
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(1, backed)
    }

    @Test
    fun applyConfirmationShareProfileToggle() {
        var shared: Boolean? = null
        composeRule.setContent {
            RealApplyConfirmationScreen(
                ApplicationFlowUiState(
                    loading = false, jobId = "job-1", job = jobDetail(), profile = profile(),
                    resume = resume(), shareProfile = true,
                ),
                onBack = {}, onRetry = {}, onCreateResume = {},
                onShareProfile = { shared = it }, onSubmit = {},
            )
        }
        composeRule.onNode(hasToggleableState()).performClick()
        assertEquals(false, shared)
    }

    @Test
    fun applyConfirmationAlreadyApplied() {
        composeRule.setContent {
            RealApplyConfirmationScreen(
                ApplicationFlowUiState(
                    loading = false, job = jobDetail(CandidateJobApplicationState.APPLIED),
                    profile = profile(), resume = resume(),
                ),
                onBack = {}, onRetry = {}, onCreateResume = {}, onShareProfile = {}, onSubmit = {},
            )
        }
        composeRule.onNodeWithText("Already applied").assertExists()
    }

    @Test
    fun applyConfirmationSubmittingShowsProgress() {
        composeRule.setContent {
            RealApplyConfirmationScreen(
                ApplicationFlowUiState(
                    loading = false, job = jobDetail(), profile = profile(), resume = resume(),
                    submitting = true,
                ),
                onBack = {}, onRetry = {}, onCreateResume = {}, onShareProfile = {}, onSubmit = {},
            )
        }
        composeRule.onNodeWithText("Submitting…").assertExists()
    }

    @Test
    fun applyConfirmationShowsInlineError() {
        composeRule.setContent {
            RealApplyConfirmationScreen(
                ApplicationFlowUiState(
                    loading = false, job = jobDetail(), profile = profile(), resume = resume(),
                    message = "Email is already used",
                ),
                onBack = {}, onRetry = {}, onCreateResume = {}, onShareProfile = {}, onSubmit = {},
            )
        }
        composeRule.onNodeWithText("Email is already used").assertExists()
    }

    // ---- RealApplicationSubmittedScreen ----

    @Test
    fun applicationSubmittedNullShowsHint() {
        var jobs = 0
        composeRule.setContent {
            RealApplicationSubmittedScreen(null, onJobs = { jobs++ }, onApplications = {})
        }
        composeRule.onNodeWithText("Submission result is no longer available. Reload the job to see its current state.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Back to jobs").performClick()
        assertEquals(1, jobs)
    }

    @Test
    fun applicationSubmittedContent() {
        var applications = 0
        var jobs = 0
        composeRule.setContent {
            RealApplicationSubmittedScreen(
                application(),
                onJobs = { jobs++ }, onApplications = { applications++ },
            )
        }
        composeRule.onNodeWithText("Application submitted").assertIsDisplayed()
        composeRule.onNodeWithText("Backend Engineer").assertExists()
        composeRule.onNodeWithText("Real Company").assertExists()
        composeRule.onNodeWithText("Status: APPLIED").assertExists()
        composeRule.onNodeWithText("Application ID: app-1").assertExists()
        composeRule.onNodeWithText("Application review").performScrollTo().assertExists()
        composeRule.onNodeWithText("View my applications").performScrollTo().performClick()
        assertEquals(1, applications)
        composeRule.onNodeWithText("Back to jobs").performClick()
        assertEquals(1, jobs)
    }

    // ---- RealMyApplicationsScreen ----

    @Test
    fun myApplicationsLoading() {
        composeRule.setContent {
            RealMyApplicationsScreen(ApplicationListUiState(loading = true), {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("My applications").assertIsDisplayed()
    }

    @Test
    fun myApplicationsErrorShowsRetry() {
        var retried = 0
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(loading = false, message = "Network unavailable"),
                onBack = {}, onTab = {}, onRefresh = {}, onRetry = { retried++ },
                onFilter = {}, onLoadMore = {}, onApplication = {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun myApplicationsEmpty() {
        composeRule.setContent {
            RealMyApplicationsScreen(ApplicationListUiState(loading = false), {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("No applications in this group yet.").assertIsDisplayed()
    }

    @Test
    fun myApplicationsContentRowsAndRefresh() {
        var refreshed = 0
        var opened: String? = null
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(
                    loading = false,
                    applications = listOf(summary(status = ApplicationStatus.OFFERED), summary(applicationId = "app-2", match = 55, jobTitle = "Data Engineer", companyName = "Other Co")),
                    counts = ApplicationCounts(3, 1, 0),
                ),
                onBack = {}, onTab = {}, onRefresh = { refreshed++ }, onRetry = {},
                onFilter = {}, onLoadMore = {}, onApplication = { opened = it },
            )
        }
        composeRule.onNodeWithText("Backend Engineer").assertIsDisplayed()
        composeRule.onNodeWithText("Real Company").assertIsDisplayed()
        composeRule.onNodeWithText("Offer received").assertExists()
        composeRule.onNodeWithText("90% match").assertExists()
        composeRule.onNodeWithText("Refresh").performClick()
        assertEquals(1, refreshed)
        composeRule.onNodeWithText("Backend Engineer").performClick()
        assertEquals("app-1", opened)
    }

    @Test
    fun myApplicationsShowsScheduledInterviewAndTimeline() {
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(
                    loading = false,
                    applications = listOf(summary(scheduledAt = "2026-08-20T14:00:00+08:00")),
                    counts = ApplicationCounts(1, 1, 0),
                ),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Interview Aug 20, 2026 14:00", substring = true).assertExists()
        composeRule.onNodeWithText("Applied", substring = true).assertExists()
    }

    @Test
    fun myApplicationsFilterChipsShowCountsAndSelect() {
        var selected: ApplicationListFilter? = null
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(loading = false, counts = ApplicationCounts(3, 1, 5)),
                onBack = {}, onTab = {}, onRefresh = {}, onRetry = {},
                onFilter = { selected = it }, onLoadMore = {}, onApplication = {},
            )
        }
        composeRule.onNodeWithText("Active", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Archived", substring = true).performClick()
        assertEquals(ApplicationListFilter.ARCHIVED, selected)
    }

    @Test
    fun myApplicationsLoadMore() {
        var loaded = 0
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(loading = false, applications = listOf(summary()), hasNext = true),
                onBack = {}, onTab = {}, onRefresh = {}, onRetry = {},
                onFilter = {}, onLoadMore = { loaded++ }, onApplication = {},
            )
        }
        composeRule.onNodeWithText("Load more").performScrollTo().performClick()
        assertEquals(1, loaded)
    }

    @Test
    fun myApplicationsLoadingMoreLabel() {
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(loading = false, applications = listOf(summary()), loadingMore = true, hasNext = true),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Loading…").assertExists()
    }

    @Test
    fun myApplicationsRefreshingLabel() {
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(loading = false, refreshing = true),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Refreshing…").assertExists()
    }

    @Test
    fun myApplicationsBottomBarSwitchesTab() {
        var tab: MainTab? = null
        composeRule.setContent {
            RealMyApplicationsScreen(
                ApplicationListUiState(loading = false),
                onBack = {}, onTab = { tab = it }, onRefresh = {}, onRetry = {},
                onFilter = {}, onLoadMore = {}, onApplication = {},
            )
        }
        composeRule.onNodeWithText("Jobs").performClick()
        assertEquals(MainTab.Jobs, tab)
    }

    // ---- RealApplicationDetailScreen ----

    @Test
    fun detailLoading() {
        composeRule.setContent {
            RealApplicationDetailScreen(ApplicationDetailUiState(loading = true), {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Application detail").assertIsDisplayed()
    }

    @Test
    fun detailNotFoundShowsRetry() {
        var retried = 0
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, notFound = true),
                onBack = {}, onRetry = { retried++ }, onRequestWithdraw = {}, onDismissWithdraw = {},
                onWithdrawReason = {}, onConfirmWithdraw = {},
            )
        }
        composeRule.onNodeWithText("This application was not found.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun detailErrorWithMessageAndBack() {
        var backed = 0
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, message = "Server exploded"),
                onBack = { backed++ }, onRetry = {}, onRequestWithdraw = {}, onDismissWithdraw = {},
                onWithdrawReason = {}, onConfirmWithdraw = {},
            )
        }
        composeRule.onNodeWithText("Server exploded").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backed)
    }

    @Test
    fun detailContentShowsInterviewTimelineAndWithdraw() {
        var withdraws = 0
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application()),
                onBack = {}, onRetry = {}, onRequestWithdraw = { withdraws++ }, onDismissWithdraw = {},
                onWithdrawReason = {}, onConfirmWithdraw = {},
            )
        }
        composeRule.onNodeWithText("Backend Engineer").assertExists()
        composeRule.onNodeWithText("88% match").assertExists()
        composeRule.onNodeWithText("Interview").assertExists()
        composeRule.onNodeWithText("When").assertExists()
        composeRule.onNodeWithText("Status timeline").assertExists()
        composeRule.onNodeWithText("Senior Engineer").assertExists()
        composeRule.onNodeWithText("Withdraw application").performScrollTo().performClick()
        assertEquals(1, withdraws)
    }

    @Test
    fun detailContentShowsGoogleMeetMeetingLink() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(
                    interview = interview(provider = MeetingProvider.GOOGLE_MEET, sync = MeetingSyncStatus.READY),
                )),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Google Meet").assertIsDisplayed()
        composeRule.onNodeWithText("https://meet.google.com/abc").assertExists()
        composeRule.onNodeWithText("Mode").assertExists()
        composeRule.onNodeWithText("Online").assertExists()
    }

    @Test
    fun detailNoInterview() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(interview = null)),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Interview not scheduled").assertExists()
    }

    @Test
    fun detailWithdrawHiddenForRejected() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application(ApplicationStatus.REJECTED)),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Withdraw application").assertDoesNotExist()
    }

    @Test
    fun detailCancelledInterview() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(
                    interview = interview(status = InterviewStatus.CANCELLED),
                )),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("This interview was cancelled.").assertExists()
        composeRule.onNodeWithText("Meeting link").assertDoesNotExist()
    }

    @Test
    fun detailCompletedInterview() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(
                    interview = interview(status = InterviewStatus.COMPLETED),
                )),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("This interview is completed.").assertExists()
    }

    @Test
    fun detailOffsiteInterviewShowsLocationText() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(
                    interview = interview(mode = InterviewMode.ONSITE, location = "12 Marina Blvd"),
                )),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Location").assertExists()
        composeRule.onNodeWithText("12 Marina Blvd").assertExists()
    }

    @Test
    fun detailGoogleMeetPendingHint() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(
                    interview = interview(provider = MeetingProvider.GOOGLE_MEET, sync = MeetingSyncStatus.PENDING),
                )),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Interview update in progress. Your current invitation remains available.")
            .assertExists()
    }

    @Test
    fun detailGoogleMeetFailedWithLinkHint() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(
                    interview = interview(provider = MeetingProvider.GOOGLE_MEET, sync = MeetingSyncStatus.FAILED),
                )),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Meeting update could not be completed. Your current invitation is unchanged.")
            .assertExists()
    }

    @Test
    fun detailGoogleMeetFailedWithoutLinkHint() {
        composeRule.setContent {
            RealApplicationDetailScreen(
                ApplicationDetailUiState(loading = false, application = application().copy(
                    interview = interview(provider = MeetingProvider.GOOGLE_MEET, sync = MeetingSyncStatus.FAILED, location = null),
                )),
                {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Meeting invitation is not available yet. Please check back later.")
            .assertExists()
    }
}