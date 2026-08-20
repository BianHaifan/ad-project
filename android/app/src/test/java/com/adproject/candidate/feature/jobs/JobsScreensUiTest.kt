package com.adproject.candidate.feature.jobs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.data.contract.CandidateJobApplicationState
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.WorkplaceType
import com.adproject.candidate.data.model.CandidateProfile
import com.adproject.candidate.data.model.Job
import com.adproject.candidate.data.model.JobDetailData
import com.adproject.candidate.data.model.JobFeedData
import com.adproject.candidate.data.model.ProfileStat
import com.adproject.candidate.data.model.ProfileTool
import com.adproject.candidate.data.model.ProfileToolGroup
import com.adproject.candidate.data.model.RecruiterContact
import com.adproject.candidate.feature.profile.SALARY_OPTIONS
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class JobsScreensUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun job(id: String = "job-1", saved: Boolean = false) = Job(
        jobId = id,
        title = "Backend Engineer",
        company = "Real Company",
        companyInitial = "R",
        companyMeta = "Series A · 51-200",
        salary = "SGD 5000–8000 / month",
        skills = listOf("Python", "FastAPI"),
        match = 90,
        recruiter = RecruiterContact("rec-1", "Mia Chen", "Hiring Manager"),
        companyId = "company-1",
        isSaved = saved,
    )

    @Test
    fun jobFeedLoadingShowsProgress() {
        composeRule.setContent {
            JobFeedScreen(JobFeedUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Recommended for you").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun jobFeedErrorShowsRetry() {
        composeRule.setContent {
            JobFeedScreen(
                JobFeedUiState(loading = false, message = "Network unavailable"),
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun jobFeedEmptyShowsNoResults() {
        composeRule.setContent {
            JobFeedScreen(
                JobFeedUiState(loading = false, data = JobFeedData("Search recommended jobs", emptyList())),
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("No matching jobs found").assertIsDisplayed()
        composeRule.onNodeWithText("Try another title or employment type.").assertIsDisplayed()
    }

    @Test
    fun jobFeedContentShowsCardsAndInvokesActions() {
        var toggleSave = 0
        var openJob: String? = null
        var openTab: MainTab? = null
        composeRule.setContent {
            JobFeedScreen(
                state = JobFeedUiState(
                    loading = false,
                    data = JobFeedData(
                        "Search recommended jobs",
                        listOf(job()),
                        recommendationSource = "MODEL",
                        modelVersion = "match-hgb-retrieval-v4",
                    ),
                    employmentType = EmploymentType.FULL_TIME,
                    workplaceType = WorkplaceType.REMOTE,
                    location = "Singapore",
                    minimumSalary = 5000,
                    hasNext = true,
                    total = 1,
                ),
                onQuery = {},
                onSearch = {},
                onEmploymentType = {},
                onWorkplaceType = {},
                onLocation = {},
                onMinimumSalary = {},
                onClearFilters = {},
                onToggleSave = { toggleSave++ },
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onRetryLoadMore = {},
                onTab = { openTab = it },
                onJob = { openJob = it },
            )
        }

        composeRule.onNodeWithText("Backend Engineer").assertIsDisplayed()
        composeRule.onNodeWithText("ML model • match-hgb-retrieval-v4").assertExists()
        composeRule.onNodeWithText("Job type: Full time", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Workplace: Remote", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Location: Singapore", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Salary: S$5,000+", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Clear all").assertExists()
        composeRule.onNodeWithText("AI Match 90%").assertExists()
        composeRule.onNodeWithText("Mia Chen").assertExists()

        composeRule.onNodeWithText("Backend Engineer").performClick()
        composeRule.onNodeWithContentDescription("Save job").performClick()
        composeRule.onNodeWithText("Jobs").performClick()

        assertEquals(1, toggleSave)
        assertEquals("job-1", openJob)
        assertEquals(MainTab.Jobs, openTab)
    }

    @Test
    fun jobFeedFilterSheetOpensAndClears() {
        composeRule.setContent {
            JobFeedScreen(
                state = JobFeedUiState(loading = false, data = JobFeedData("x", listOf(job()))),
                onQuery = {},
                onSearch = {},
                onEmploymentType = {},
                onWorkplaceType = {},
                onLocation = {},
                onMinimumSalary = {},
                onClearFilters = {},
                onToggleSave = {},
                onRefresh = {},
                onRetry = {},
                onLoadMore = {},
                onRetryLoadMore = {},
                onTab = {},
                onJob = {},
            )
        }

        composeRule.onNodeWithContentDescription("Filter jobs").performClick()
        composeRule.onNodeWithText("Filter jobs").assertIsDisplayed()
        composeRule.onNodeWithText("Job type").assertIsDisplayed()
        composeRule.onNodeWithText("Minimum salary").assertIsDisplayed()
        assertTrue(SALARY_OPTIONS.containsAll(listOf(1000L, 2000L, 2500L)))
    }

    @Test
    fun jobDetailLoadingShowsProgress() {
        composeRule.setContent {
            JobDetailScreen(
                JobDetailUiState(loading = true),
                onBack = {}, onRetry = {}, onApply = {}, onViewCompany = {}, onViewRecruiter = {},
                onToggleSave = {},
            )
        }
    }

    @Test
    fun jobDetailNotFoundShowsMessage() {
        composeRule.setContent {
            JobDetailScreen(
                JobDetailUiState(loading = false, notFound = true, message = "gone"),
                onBack = {}, onRetry = {}, onApply = {}, onViewCompany = {}, onViewRecruiter = {},
                onToggleSave = {},
            )
        }
        composeRule.onNodeWithText("This job is no longer available.").assertIsDisplayed()
    }

    @Test
    fun jobDetailErrorShowsRetry() {
        var retries = 0
        composeRule.setContent {
            JobDetailScreen(
                JobDetailUiState(loading = false, message = "Network unavailable"),
                onBack = {}, onRetry = { retries++ }, onApply = {}, onViewCompany = {},
                onViewRecruiter = {}, onToggleSave = {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun jobDetailContentRendersAnalysisRecruiterAndActions() {
        var toggleSave = 0
        var apply: String? = null
        var viewCompany: String? = null
        var viewRecruiter: String? = null
        var messageRecruiter = 0
        composeRule.setContent {
            JobDetailScreen(
                state = JobDetailUiState(
                    loading = false,
                    data = JobDetailData(
                        job = job(),
                        location = "Singapore",
                        employmentType = "Full Time",
                        workplace = "Hybrid",
                        strongMatches = "Skills matched: 2 of 3",
                        gap = "Experience is below the stated requirement",
                        description = "Build production APIs.",
                        requirements = listOf("Three years of backend development", "Experience with REST APIs"),
                        skills = listOf("Python", "FastAPI"),
                        deadline = "2026-09-01",
                        publishedAt = "2026-08-11",
                        matchAnalysisAvailable = true,
                        applicationState = CandidateJobApplicationState.NOT_APPLIED,
                    ),
                    isSaved = false,
                ),
                onBack = {},
                onRetry = {},
                onApply = { apply = it },
                onViewCompany = { viewCompany = it },
                onViewRecruiter = { viewRecruiter = it },
                onToggleSave = { toggleSave++ },
                onMessageRecruiter = { messageRecruiter++ },
            )
        }

        composeRule.onNodeWithText("Job description").assertIsDisplayed()
        composeRule.onNodeWithText("AI Match Analysis").assertExists()
        composeRule.onNodeWithText("Strong matches\nSkills matched: 2 of 3").assertExists()
        composeRule.onNodeWithText("Gaps\nExperience is below the stated requirement").assertExists()
        composeRule.onNodeWithText("Requirements").assertExists()
        composeRule.onNodeWithText("Three years of backend development").assertExists()
        composeRule.onNodeWithText("Experience with REST APIs").assertExists()
        composeRule.onNodeWithText("Deadline: 2026-09-01").assertExists()
        composeRule.onNodeWithText("Published: 2026-08-11").assertExists()

        composeRule.onNodeWithContentDescription("Save job").performClick()
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.onNodeWithText("Real Company").performClick()
        composeRule.onNodeWithText("Mia Chen").performScrollTo().performClick()
        composeRule.onNodeWithText("Message").performClick()

        assertEquals(1, toggleSave)
        assertEquals("job-1", apply)
        assertEquals("company-1", viewCompany)
        assertEquals("rec-1", viewRecruiter)
        assertEquals(1, messageRecruiter)
    }

    @Test
    fun jobDetailShowsAppliedStateAndNoAnalysis() {
        composeRule.setContent {
            JobDetailScreen(
                state = JobDetailUiState(
                    loading = false,
                    data = JobDetailData(
                        job = job().copy(match = null),
                        location = "Singapore",
                        employmentType = "Full Time",
                        workplace = "Hybrid",
                        strongMatches = "",
                        gap = "",
                        description = "desc",
                        requirements = emptyList(),
                        applicationState = CandidateJobApplicationState.INTERVIEW,
                    ),
                ),
                onBack = {}, onRetry = {}, onApply = {}, onViewCompany = {}, onViewRecruiter = {},
                onToggleSave = {},
            )
        }
        composeRule.onNodeWithText("Interview").assertIsDisplayed()
        composeRule.onNodeWithText("Match analysis is not available yet.").assertExists()
    }

    @Test
    fun savedJobsLoadingShowsProgress() {
        composeRule.setContent {
            SavedJobsScreen(SavedJobsUiState(loading = true), {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Saved jobs").assertIsDisplayed()
    }

    @Test
    fun savedJobsErrorShowsRetry() {
        composeRule.setContent {
            SavedJobsScreen(
                SavedJobsUiState(loading = false, message = "Network unavailable"),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun savedJobsEmptyShowsHint() {
        composeRule.setContent {
            SavedJobsScreen(SavedJobsUiState(loading = false, jobs = emptyList()), {}, {}, {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("No saved jobs yet").assertIsDisplayed()
        composeRule.onNodeWithText("Tap Save on a job to keep it here.").assertIsDisplayed()
    }

    @Test
    fun savedJobsContentShowsCardsAndFooterStates() {
        var unsave: String? = null
        composeRule.setContent {
            SavedJobsScreen(
                state = SavedJobsUiState(
                    loading = false,
                    jobs = listOf(job(saved = true)),
                    total = 1,
                    saveError = "Unable to remove this job.",
                ),
                onBack = {},
                onRetry = {},
                onRefresh = {},
                onLoadMore = {},
                onRetryLoadMore = {},
                onJob = {},
                onUnsave = { unsave = it },
            )
        }

        composeRule.onNodeWithText("1 saved").assertIsDisplayed()
        composeRule.onNodeWithText("Unable to remove this job.").assertExists()
        composeRule.onNodeWithContentDescription("Remove saved job").performClick()
        composeRule.onNodeWithText("You're all caught up").assertExists()
        assertEquals("job-1", unsave)
    }

    @Test
    fun savedJobsPaginationFooterStatesRender() {
        composeRule.setContent {
            SavedJobsScreen(
                SavedJobsUiState(loading = false, jobs = listOf(job()), loadingMore = true),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Saved jobs").assertIsDisplayed()
    }

    @Test
    fun savedJobsLoadMoreErrorShowsRetry() {
        var retries = 0
        composeRule.setContent {
            SavedJobsScreen(
                SavedJobsUiState(loading = false, jobs = listOf(job()), loadMoreError = true),
                {}, {}, {}, {}, { retries++ }, {}, {},
            )
        }
        composeRule.onNodeWithText("Couldn't load more jobs.").assertExists()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun profileScreenRendersStatsAndTools() {
        composeRule.setContent {
            ProfileScreen(
                data = CandidateProfile(
                    fullName = "Alex Tan",
                    headline = "Backend Python Engineer",
                    stats = listOf(
                        ProfileStat("5", "Applied"),
                        ProfileStat("2", "Interviews"),
                    ),
                    toolGroups = listOf(
                        ProfileToolGroup(
                            "Career tools",
                            listOf(
                                ProfileTool("📄", "Resume", "resume"),
                                ProfileTool("🎯", "Saved jobs", null),
                            ),
                        ),
                    ),
                ),
                onTab = {},
                onApplications = {},
                onResume = {},
                onLogout = {},
            )
        }

        composeRule.onNodeWithText("Alex Tan").assertIsDisplayed()
        composeRule.onNodeWithText("Backend Python Engineer").assertIsDisplayed()
        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("Resume").assertExists()
        composeRule.onNodeWithText("Saved jobs").assertExists()
        composeRule.onNodeWithText("Sign out").assertExists()
        composeRule.onNodeWithText("Career tools").assertExists()
    }
}

private fun assertEquals(expected: Int, actual: Int) {
    org.junit.Assert.assertEquals(expected, actual)
}

private fun assertEquals(expected: String?, actual: String?) {
    org.junit.Assert.assertEquals(expected, actual)
}

private fun assertEquals(expected: MainTab?, actual: MainTab?) {
    org.junit.Assert.assertEquals(expected, actual)
}
