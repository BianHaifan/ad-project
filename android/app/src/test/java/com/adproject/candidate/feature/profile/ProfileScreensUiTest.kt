package com.adproject.candidate.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adproject.candidate.data.contract.ApplicationCounts
import com.adproject.candidate.data.contract.CandidateProfileDto
import com.adproject.candidate.data.contract.CandidateStats
import com.adproject.candidate.data.contract.CompanyPublicProfile
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.Experience
import com.adproject.candidate.data.contract.Gender
import com.adproject.candidate.data.contract.JobPreference
import com.adproject.candidate.data.contract.PublicCompanySummary
import com.adproject.candidate.data.contract.RecruiterPublicProfile
import com.adproject.candidate.data.contract.Resume
import com.adproject.candidate.data.contract.WorkplaceType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ProfileScreensUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun profile(
        avatarUrl: String? = "/api/v1/avatars/u1",
        headline: String = "Senior Backend Engineer",
        gender: Gender? = Gender.MALE,
        age: Int? = 31,
        location: String = "Central",
        phone: String? = "+65 9123 4567",
        birthplace: String? = "Kuala Lumpur",
    ) = CandidateProfileDto(
        userId = "u1",
        fullName = "Alice Ng",
        email = "alice@example.com",
        headline = headline,
        avatarUrl = avatarUrl,
        location = location,
        age = age,
        gender = gender,
        phone = phone,
        birthplace = birthplace,
        stats = CandidateStats(3, 5, 1, 2),
        version = 4,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun resume(
        summary: String = "Backend focused developer.",
        skills: List<String> = listOf("Kotlin", "SQL"),
        experiences: List<Experience> = listOf(
            Experience("e1", "Software Engineer", "Acme", "Built APIs.", "2024-01", "2025-01"),
        ),
    ) = Resume(
        resumeId = "r1",
        fullName = "Alice Ng",
        age = 31,
        location = "Central",
        headline = "Senior Backend Engineer",
        summary = summary,
        experiences = experiences,
        version = 2,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        skills = skills,
    )

    @Test
    fun realProfileLoadingShowsSpinner() {
        composeRule.setContent {
            RealProfileScreen(ProfileUiState(), ResumeUiState(), ApplicationCounts(0, 0, 0), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun realProfileErrorShowsRetry() {
        var retries = 0
        composeRule.setContent {
            RealProfileScreen(
                ProfileUiState(loading = false, message = "Network unavailable"),
                ResumeUiState(), ApplicationCounts(0, 0, 0),
                onRetry = { retries++ }, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun realProfileErrorWithNullMessage() {
        composeRule.setContent {
            RealProfileScreen(
                ProfileUiState(loading = false),
                ResumeUiState(), ApplicationCounts(0, 0, 0), {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Unable to load profile").assertIsDisplayed()
    }

    @Test
    fun realProfileContentShowsEntriesAndCounts() {
        composeRule.setContent {
            RealProfileScreen(
                ProfileUiState(loading = false, data = profile()),
                ResumeUiState(loading = false, data = resume()),
                ApplicationCounts(2, 1, 3),
                {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("My applications").assertIsDisplayed()
        composeRule.onNodeWithText("Track applications and interviews").assertIsDisplayed()
        composeRule.onNodeWithText("In progress").assertExists()
        composeRule.onNodeWithText("2").assertExists()
        composeRule.onNodeWithText("Interview").assertExists()
        composeRule.onNodeWithText("1").assertExists()
        composeRule.onNodeWithText("Archived").assertExists()
        composeRule.onNodeWithText("3").assertExists()
        composeRule.onNodeWithText("Resume").assertExists()
        composeRule.onNodeWithText("Complete · ready to apply").assertExists()
        composeRule.onNodeWithText("Saved jobs").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Job preferences").performScrollTo().assertExists()
        composeRule.onNodeWithText("Sign out").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun realProfileContentResumeUnavailable() {
        composeRule.setContent {
            RealProfileScreen(
                ProfileUiState(loading = false, data = profile()),
                ResumeUiState(loading = false, data = null, notCreated = false),
                ApplicationCounts(2, 1, 3), {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Unavailable").assertExists()
    }

    @Test
    fun realProfileContentResumeIncomplete() {
        composeRule.setContent {
            RealProfileScreen(
                ProfileUiState(loading = false, data = profile()),
                ResumeUiState(loading = false, data = resume(summary = " ", skills = emptyList(), experiences = emptyList())),
                ApplicationCounts(2, 1, 3), {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Add summary, skills, experience").assertExists()
    }

    @Test
    fun realProfileContentResumeLoading() {
        composeRule.setContent {
            RealProfileScreen(
                ProfileUiState(loading = false, data = profile()),
                ResumeUiState(loading = true),
                ApplicationCounts(2, 1, 3), {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Loading…").assertExists()
    }

    @Test
    fun realProfileContentClickableEntries() {
        var openProfile = 0
        var openApps = 0
        var openResume = 0
        var openSaved = 0
        var openPrefs = 0
        var logout = 0
        composeRule.setContent {
            RealProfileScreen(
                ProfileUiState(loading = false, data = profile()),
                ResumeUiState(loading = false, data = resume()),
                ApplicationCounts(2, 1, 3),
                onRetry = {},
                onOpenProfile = { openProfile++ },
                onOpenApplications = { openApps++ },
                onOpenResume = { openResume++ },
                onOpenPreferences = { openPrefs++ },
                onOpenSavedJobs = { openSaved++ },
                onLogout = { logout++ },
                onTab = {},
            )
        }
        composeRule.onNodeWithText("Alice Ng").performClick()
        assertEquals(1, openProfile)
        composeRule.onNodeWithText("My applications").performClick()
        assertEquals(1, openApps)
        composeRule.onNodeWithText("Resume").performClick()
        assertEquals(1, openResume)
        composeRule.onNodeWithText("Saved jobs").performScrollTo().performClick()
        assertEquals(1, openSaved)
        composeRule.onNodeWithText("Job preferences").performScrollTo().performClick()
        assertEquals(1, openPrefs)
        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1, logout)
    }

    @Test
    fun realProfileEditLoadingShowsSpinner() {
        composeRule.setContent {
            RealProfileEditScreen(ProfileUiState(loading = true), {}, {}, { _, _, _, _, _, _, _ -> }, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun realProfileEditErrorShowsRetry() {
        var retries = 0
        composeRule.setContent {
            RealProfileEditScreen(
                ProfileUiState(loading = false, message = "Network unavailable"),
                onBack = {}, onRetry = { retries++ }, onSave = { _, _, _, _, _, _, _ -> },
                onSelectAvatar = {}, onUploadAvatar = {}, onDeleteAvatar = {}, onCancelAvatar = {},
                onAvatarTooLarge = {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun realProfileEditFormRendersAndSaves() {
        var captured: String? = null
        var capturedGender: Gender? = null
        var capturedAge: Int? = null
        var capturedLocation: String? = null
        var capturedHeadline: String? = null
        var capturedPhone: String? = null
        var capturedBirthplace: String? = null
        composeRule.setContent {
            RealProfileEditScreen(
                ProfileUiState(loading = false, data = profile()),
                onBack = {}, onRetry = {},
                onSave = { name, gender, age, location, headline, phone, birthplace ->
                    captured = name; capturedGender = gender; capturedAge = age
                    capturedLocation = location; capturedHeadline = headline
                    capturedPhone = phone; capturedBirthplace = birthplace
                },
                onSelectAvatar = {}, onUploadAvatar = {}, onDeleteAvatar = {}, onCancelAvatar = {},
                onAvatarTooLarge = {},
            )
        }
        composeRule.onNodeWithText("Edit profile").assertIsDisplayed()
        composeRule.onNodeWithText("PNG or JPEG, up to 5 MB").assertExists()
        composeRule.onNodeWithText("Change photo").assertExists()
        composeRule.onNodeWithText("Male").performScrollTo().performClick()
        composeRule.onNodeWithText("Female").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("31").performScrollTo().performClick()
        composeRule.onNodeWithText("33").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals("Alice Ng", captured)
        assertEquals(Gender.FEMALE, capturedGender)
        assertEquals(33, capturedAge)
        assertEquals("Central", capturedLocation)
        assertEquals("Senior Backend Engineer", capturedHeadline)
        assertEquals("+65 9123 4567", capturedPhone)
        assertEquals("Kuala Lumpur", capturedBirthplace)
    }

    @Test
    fun realProfileEditFormShowsFieldErrorsAndAvatarMessage() {
        composeRule.setContent {
            RealProfileEditScreen(
                ProfileUiState(
                    loading = false,
                    data = profile(avatarUrl = null),
                    fieldErrors = mapOf("fullName" to "Full name is required", "age" to "Age must be 16–100"),
                    message = "Please correct the highlighted fields.",
                    avatar = AvatarUiState(message = "This image is larger than 5 MB."),
                ),
                onBack = {}, onRetry = {},
                onSave = { _, _, _, _, _, _, _ -> },
                onSelectAvatar = {}, onUploadAvatar = {}, onDeleteAvatar = {}, onCancelAvatar = {},
                onAvatarTooLarge = {},
            )
        }
        composeRule.onNodeWithText("Full name is required").assertIsDisplayed()
        composeRule.onNodeWithText("Age must be 16–100").assertExists()
        composeRule.onNodeWithText("This image is larger than 5 MB.").assertExists()
        composeRule.onNodeWithText("Add photo").assertExists()
    }

    @Test
    fun realProfileEditSendsAvatarAndCancelActions() {
        var uploads = 0
        composeRule.setContent {
            RealProfileEditScreen(
                ProfileUiState(
                    loading = false,
                    data = profile(),
                    avatar = AvatarUiState(
                        pending = PendingAvatar("avatar.jpg", "image/jpeg", byteArrayOf(1, 2, 3)),
                    ),
                ),
                onBack = {}, onRetry = {},
                onSave = { _, _, _, _, _, _, _ -> },
                onSelectAvatar = {}, onUploadAvatar = { uploads++ }, onDeleteAvatar = {},
                onCancelAvatar = {}, onAvatarTooLarge = {},
            )
        }
        composeRule.onNodeWithText("Upload photo").performClick()
        assertEquals(1, uploads)
    }

    @Test
    fun realProfileEditUploadingStateDisablesButtons() {
        composeRule.setContent {
            RealProfileEditScreen(
                ProfileUiState(
                    loading = false,
                    data = profile(),
                    avatar = AvatarUiState(uploading = true, deleting = false,
                        pending = PendingAvatar("avatar.jpg", "image/jpeg", byteArrayOf(1, 2, 3))),
                ),
                onBack = {}, onRetry = {},
                onSave = { _, _, _, _, _, _, _ -> },
                onSelectAvatar = {}, onUploadAvatar = {}, onDeleteAvatar = {}, onCancelAvatar = {},
                onAvatarTooLarge = {},
            )
        }
        composeRule.onNodeWithText("Uploading…").assertExists()
    }

    @Test
    fun realProfileEditDeletePhoto() {
        var deletes = 0
        composeRule.setContent {
            RealProfileEditScreen(
                ProfileUiState(loading = false, data = profile()),
                onBack = {}, onRetry = {},
                onSave = { _, _, _, _, _, _, _ -> },
                onSelectAvatar = {}, onUploadAvatar = {}, onDeleteAvatar = { deletes++ },
                onCancelAvatar = {}, onAvatarTooLarge = {},
            )
        }
        composeRule.onNodeWithText("Remove photo").performClick()
        assertEquals(1, deletes)
    }

    @Test
    fun realResumeLoadingShowsSpinner() {
        composeRule.setContent {
            RealResumeEditScreen(ResumeUiState(loading = true), {}, {}, { _, _, _ -> })
        }
    }

    @Test
    fun realResumeNotCreatedShowsIntro() {
        composeRule.setContent {
            RealResumeEditScreen(ResumeUiState(loading = false, notCreated = true), {}, {}, { _, _, _ -> })
        }
        composeRule.onNodeWithText("Create your resume").assertIsDisplayed()
        composeRule.onNodeWithText("Select skills").assertExists()
    }

    @Test
    fun realResumeErrorShowsRetry() {
        var retries = 0
        composeRule.setContent {
            RealResumeEditScreen(
                ResumeUiState(loading = false, message = "Couldn't load your resume."),
                onBack = {}, onRetry = { retries++ }, onSave = { _, _, _ -> },
            )
        }
        composeRule.onNodeWithText("Couldn't load your resume.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun realResumeEditFormSavesSkillsAndExperiences() {
        var summary = ""
        var skills = emptyList<String>()
        var experiences = emptyList<Experience>()
        composeRule.setContent {
            RealResumeEditScreen(
                ResumeUiState(loading = false, data = resume(skills = listOf("Kotlin"))),
                onBack = {}, onRetry = {},
                onSave = { s, sk, ex -> summary = s; skills = sk; experiences = ex },
            )
        }
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithText("Experience 1").assertExists()
        composeRule.onNodeWithText("+ Add experience").performScrollTo().performClick()
        composeRule.onNodeWithText("Experience 2").assertExists()
        composeRule.onNodeWithText("Save resume").performScrollTo().performClick()
        assertEquals("Backend focused developer.", summary)
        assertEquals(listOf("Kotlin"), skills)
        assertEquals(2, experiences.size)
        assertEquals("2026-01", experiences[1].startDate)
    }

    @Test
    fun realResumeEditFormShowsErrors() {
        composeRule.setContent {
            RealResumeEditScreen(
                ResumeUiState(
                    loading = false,
                    data = resume(),
                    fieldErrors = mapOf(
                        "summary" to "Summary is required",
                        "experiences[0].title" to "Title is required",
                    ),
                ),
                onBack = {}, onRetry = {}, onSave = { _, _, _ -> },
            )
        }
        composeRule.onNodeWithText("Summary is required").assertIsDisplayed()
        composeRule.onNodeWithText("Title is required").assertExists()
    }

    @Test
    fun jobPreferencesLoadingShowsSpinner() {
        composeRule.setContent {
            JobPreferencesScreen(JobPreferenceUiState(loading = true), {}, {}, { _, _, _, _, _ -> })
        }
    }

    @Test
    fun jobPreferencesErrorShowsRetryAndBack() {
        var retries = 0
        var backs = 0
        composeRule.setContent {
            JobPreferencesScreen(
                JobPreferenceUiState(loading = false, message = "Network unavailable"),
                onRetry = { retries++ }, onBack = { backs++ }, onSave = { _, _, _, _, _ -> },
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun jobPreferencesFormSelectsAndSaves() {
        var titles = emptyList<String>()
        var locations = emptyList<String>()
        var workplaces = emptySet<WorkplaceType>()
        var employments = emptySet<EmploymentType>()
        var salary: Long? = null
        composeRule.setContent {
            JobPreferencesScreen(
                JobPreferenceUiState(
                    loading = false,
                    data = JobPreference(
                        desiredTitles = listOf("Backend Engineer"),
                        preferredLocations = listOf("Singapore"),
                        workplaceTypes = listOf(WorkplaceType.HYBRID),
                        employmentTypes = listOf(EmploymentType.PART_TIME),
                        minimumSalary = null,
                        salaryCurrency = "SGD",
                        salaryPeriod = "MONTH",
                        version = 3,
                        createdAt = null,
                        updatedAt = null,
                    ),
                ),
                onRetry = {},
                onBack = {},
                onSave = { t, l, w, e, s -> titles = t; locations = l; workplaces = w; employments = e; salary = s },
            )
        }
        composeRule.onNodeWithText("Job preferences").assertIsDisplayed()
        composeRule.onNodeWithText("Backend Engineer").assertExists()
        composeRule.onNodeWithText("Singapore").assertExists()
        composeRule.onNodeWithText("HYBRID").performScrollTo().performClick()
        composeRule.onNodeWithText("ONSITE").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("PART TIME").performScrollTo().performClick()
        composeRule.onNodeWithText("FULL TIME").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("Not specified").performScrollTo().performClick()
        composeRule.onNodeWithText("S$6,000").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("Save preferences").performScrollTo().performClick()
        assertEquals(listOf("Backend Engineer"), titles)
        assertEquals(listOf("Singapore"), locations)
        assertEquals(setOf(WorkplaceType.HYBRID, WorkplaceType.ONSITE), workplaces)
        assertEquals(setOf(EmploymentType.PART_TIME, EmploymentType.FULL_TIME), employments)
        assertEquals(6000L, salary)
    }

    @Test
    fun jobPreferencesSavedMessageShown() {
        composeRule.setContent {
            JobPreferencesScreen(
                JobPreferenceUiState(
                    loading = false,
                    saved = true,
                    data = JobPreference(emptyList(), emptyList(), emptyList(), emptyList(), null,
                        "SGD", "MONTH", 1, null, null),
                ),
                {}, {}, { _, _, _, _, _ -> },
            )
        }
        composeRule.onNodeWithText("Preferences saved. Refresh Recommended jobs to recalculate matches.")
            .assertExists()
    }

    @Test
    fun recruiterPublicProfileLoadingShowsSpinner() {
        composeRule.setContent {
            RecruiterPublicProfileScreen(RecruiterPublicProfileUiState(loading = true), {}, {})
        }
    }

    @Test
    fun recruiterPublicProfileErrorShowsRetry() {
        var retries = 0
        composeRule.setContent {
            RecruiterPublicProfileScreen(
                RecruiterPublicProfileUiState(loading = false, message = "Network unavailable"),
                onBack = {}, onRetry = { retries++ },
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun recruiterPublicProfileNotFoundHidesRetry() {
        var retries = 0
        composeRule.setContent {
            RecruiterPublicProfileScreen(
                RecruiterPublicProfileUiState(loading = false, notFound = true, message = "gone"),
                onBack = {}, onRetry = { retries++ },
            )
        }
        composeRule.onNodeWithText("This recruiter is no longer available.").assertIsDisplayed()
    }

    @Test
    fun recruiterPublicProfileContentRenders() {
        composeRule.setContent {
            RecruiterPublicProfileScreen(
                RecruiterPublicProfileUiState(
                    loading = false,
                    data = RecruiterPublicProfile(
                        recruiterId = "r1",
                        fullName = "Mia Chen",
                        avatarUrl = null,
                        title = "Hiring Manager",
                        bio = "I lead backend hiring.",
                        company = PublicCompanySummary("c1", "Acme Corp", null, "APPROVED"),
                    ),
                ),
                onBack = {}, onRetry = {},
            )
        }
        composeRule.onNodeWithText("Mia Chen").assertIsDisplayed()
        composeRule.onNodeWithText("Hiring Manager").assertExists()
        composeRule.onNodeWithText("About").assertExists()
        composeRule.onNodeWithText("I lead backend hiring.").assertExists()
        composeRule.onNodeWithText("Company").performScrollTo().assertExists()
        composeRule.onNodeWithText("Acme Corp").performScrollTo().assertExists()
        composeRule.onNodeWithText("Verified").performScrollTo().assertExists()
    }

    @Test
    fun companyPublicProfileLoadingShowsSpinner() {
        composeRule.setContent {
            CompanyPublicProfileScreen(CompanyPublicProfileUiState(loading = true), {}, {})
        }
    }

    @Test
    fun companyPublicProfileNotFoundHidesRetry() {
        var retries = 0
        composeRule.setContent {
            CompanyPublicProfileScreen(
                CompanyPublicProfileUiState(loading = false, notFound = true, message = "gone"),
                onBack = {}, onRetry = { retries++ },
            )
        }
        composeRule.onNodeWithText("This company is no longer available.").assertIsDisplayed()
    }

    private fun companyScreen(status: String?) {
        composeRule.setContent {
            CompanyPublicProfileScreen(
                CompanyPublicProfileUiState(
                    loading = false,
                    data = CompanyPublicProfile("c1", "Acme Corp", null, "Great place.", "Singapore", status),
                ),
                onBack = {}, onRetry = {},
            )
        }
    }

    @Test
    fun companyPublicProfileContentVerified() {
        companyScreen("APPROVED")
        composeRule.onNodeWithText("Acme Corp").assertIsDisplayed()
        composeRule.onNodeWithText("Great place.").assertExists()
        composeRule.onNodeWithText("Location: Singapore").assertExists()
        composeRule.onNodeWithText("Verified").assertExists()
    }

    @Test
    fun companyPublicProfileContentPending() {
        companyScreen("PENDING")
        composeRule.onNodeWithText("Acme Corp").assertIsDisplayed()
        composeRule.onNodeWithText("Verification pending").assertExists()
    }

    @Test
    fun companyPublicProfileContentRejected() {
        companyScreen("REJECTED")
        composeRule.onNodeWithText("Acme Corp").assertIsDisplayed()
        composeRule.onNodeWithText("Not verified").assertExists()
    }

    @Test
    fun companyPublicProfileContentCustomStatus() {
        companyScreen("SUSPENDED")
        composeRule.onNodeWithText("Acme Corp").assertIsDisplayed()
        composeRule.onNodeWithText("Suspended").assertExists()
    }

    @Test
    fun companyPublicProfileWithoutDetailsHidesAboutCard() {
        composeRule.setContent {
            CompanyPublicProfileScreen(
                CompanyPublicProfileUiState(
                    loading = false,
                    data = CompanyPublicProfile("c1", "Acme Corp", null, null, null, null),
                ),
                onBack = {}, onRetry = {},
            )
        }
        composeRule.onNodeWithText("Acme Corp").assertIsDisplayed()
        composeRule.onNodeWithText("About").assertDoesNotExist()
    }

    @Test
    fun singleSelectSheetConfirmsSelection() {
        var captured: String? = "sentinel"
        composeRule.setContent {
            SingleSelectSheet(
                title = "Pick one",
                options = listOf("Alpha", "Beta"),
                optionLabel = { it },
                initialSelected = "Alpha",
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Pick one").assertExists()
        composeRule.onNodeWithText("Beta").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        assertEquals("Beta", captured)
    }

    @Test
    fun singleSelectSheetClearRowSetsNull() {
        var captured: String? = "sentinel"
        composeRule.setContent {
            SingleSelectSheet(
                title = "Pick one",
                options = listOf("Alpha"),
                optionLabel = { it },
                initialSelected = "Alpha",
                clearLabel = "Clear me",
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Clear me").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        assertEquals(null, captured)
    }

    @Test
    fun enumMultiSelectSheetTogglesAndConfirms() {
        var captured = emptySet<WorkplaceType>()
        composeRule.setContent {
            EnumMultiSelectSheet(
                title = "Workplace type",
                options = WorkplaceType.entries,
                optionLabel = { it.name.replace('_', ' ') },
                initialSelected = setOf(WorkplaceType.REMOTE),
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithText("ONSITE").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        assertEquals(setOf(WorkplaceType.REMOTE, WorkplaceType.ONSITE), captured)
    }

    @Test
    fun numberWheelSheetSelectsValue() {
        var captured: Long? = null
        composeRule.setContent {
            NumberWheelSheet(
                title = "Minimum monthly salary (SGD)",
                values = listOf(3000L, 4000L, 5000L, 6000L, 7000L, 8000L),
                labelOf = { it.toString() },
                initialValue = null,
                clearLabel = "Not specified",
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("6000").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        assertEquals(6000L, captured)
    }

    @Test
    fun numberWheelSheetClearRowSetsNull() {
        var captured: Long? = 999L
        composeRule.setContent {
            NumberWheelSheet(
                title = "Minimum monthly salary (SGD)",
                values = listOf(3000L, 4000L),
                labelOf = { "S$${it},000" },
                initialValue = 4000L,
                clearLabel = "Not specified",
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Not specified").performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        assertEquals(null, captured)
    }

    @Test
    fun numberWheelRendersAndSelectsOnClick() {
        var captured: Int? = null
        composeRule.setContent {
            NumberWheel(
                values = listOf(16, 17, 18),
                initialValue = null,
                labelOf = { it.toString() },
                onValueChange = { captured = it },
            )
        }
        composeRule.onNodeWithText("16").performClick()
        assertEquals(16, captured)
    }
}