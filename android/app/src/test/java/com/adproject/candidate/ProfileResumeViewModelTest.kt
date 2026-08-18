package com.adproject.candidate

import com.adproject.candidate.data.api.*
import com.adproject.candidate.data.contract.*
import com.adproject.candidate.feature.profile.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileResumeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun profileLoadsEditsAndPreventsDuplicateSave() = runTest(main.dispatcher) {
        val repository = ProfileFake()
        val viewModel = CandidateProfileViewModel(repository, AvatarFake())
        advanceUntilIdle()
        assertEquals("Candidate", viewModel.state.value.data?.fullName)
        viewModel.edit()
        viewModel.save("Updated", null, null, "Singapore", "Engineer", "", "")
        viewModel.save("Duplicate", null, null, "Singapore", "Engineer", "", "")
        advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        repository.pending.complete(ApiResult.Success(profile("Updated", 2)))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saved)
        assertEquals("Updated", viewModel.state.value.data?.fullName)
        assertFalse(viewModel.state.value.editing)
        assertFalse(viewModel.state.value.submitting)
    }

    @Test fun profileValidationAndRetryErrorAreSafe() = runTest(main.dispatcher) {
        val failing = ProfileFake(getResult = ApiResult.Failure("Safe profile error"))
        val viewModel = CandidateProfileViewModel(failing, AvatarFake())
        advanceUntilIdle()
        assertEquals("Safe profile error", viewModel.state.value.message)
        val valid = CandidateProfileViewModel(ProfileFake(), AvatarFake())
        advanceUntilIdle(); valid.edit(); valid.save("", null, null, "", "", "", "")
        assertEquals("Full name is required", valid.state.value.fieldErrors["fullName"])
    }

    @Test fun profileValidationRejectsBadPhoneAndLongBirthplace() = runTest(main.dispatcher) {
        val repository = ProfileFake()
        val viewModel = CandidateProfileViewModel(repository, AvatarFake())
        advanceUntilIdle(); viewModel.edit()
        viewModel.save("Candidate", Gender.FEMALE, null, "Singapore", "Engineer", "not-a-phone", "")
        assertEquals("Enter a valid phone number", viewModel.state.value.fieldErrors["phone"])
        viewModel.save("Candidate", Gender.FEMALE, null, "Singapore", "Engineer", "", "x".repeat(101))
        assertEquals("Maximum 100 characters", viewModel.state.value.fieldErrors["birthplace"])
        advanceUntilIdle()
        assertEquals(0, repository.saveCalls)
    }

    @Test fun profileSaveSendsIdentityFields() = runTest(main.dispatcher) {
        val repository = ProfileFake()
        val viewModel = CandidateProfileViewModel(repository, AvatarFake())
        advanceUntilIdle(); viewModel.edit()
        viewModel.save("Candidate", Gender.FEMALE, 27, "Singapore", "Engineer", "+65 1234 5678", "Singapore")
        advanceUntilIdle()
        assertEquals(Gender.FEMALE, repository.lastRequest?.gender)
        assertEquals("+65 1234 5678", repository.lastRequest?.phone)
        assertEquals("Singapore", repository.lastRequest?.birthplace)
        assertEquals(27, repository.lastRequest?.age)
        assertEquals("Singapore", repository.lastRequest?.location)
        repository.pending.complete(ApiResult.Success(profile(gender = Gender.FEMALE, phone = "+65 1234 5678", birthplace = "Singapore", age = 27)))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saved)
        assertEquals(Gender.FEMALE, viewModel.state.value.data?.gender)
        assertEquals("+65 1234 5678", viewModel.state.value.data?.phone)
        assertEquals("Singapore", viewModel.state.value.data?.birthplace)
        assertEquals(27, viewModel.state.value.data?.age)
    }

    @Test fun profileValidationRejectsBadAgeAndMissingLocation() = runTest(main.dispatcher) {
        val repository = ProfileFake()
        val viewModel = CandidateProfileViewModel(repository, AvatarFake())
        advanceUntilIdle(); viewModel.edit()
        viewModel.save("Candidate", null, 150, "", "Engineer", "", "")
        assertEquals("Age must be 16–100", viewModel.state.value.fieldErrors["age"])
        assertEquals("Location is required", viewModel.state.value.fieldErrors["location"])
        advanceUntilIdle()
        assertEquals(0, repository.saveCalls)
    }

    @Test fun profileSaveTurnsBlankPhoneBirthplaceAndMissingGenderIntoNull() = runTest(main.dispatcher) {
        val repository = ProfileFake()
        val viewModel = CandidateProfileViewModel(repository, AvatarFake())
        advanceUntilIdle(); viewModel.edit()
        viewModel.save("Candidate", null, null, "Singapore", "Engineer", "   ", "  ")
        advanceUntilIdle()
        assertNull(repository.lastRequest?.gender)
        assertNull(repository.lastRequest?.phone)
        assertNull(repository.lastRequest?.birthplace)
    }

    @Test fun resumeEditAndCancelEditManageEditingState() = runTest(main.dispatcher) {
        val viewModel = CandidateResumeViewModel(ResumeFake(mutableListOf(ApiResult.Success(resume(3)))))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.editing)
        viewModel.edit()
        assertTrue(viewModel.state.value.editing)
        assertFalse(viewModel.state.value.saved)
        viewModel.cancelEdit()
        assertFalse(viewModel.state.value.editing)
    }

    @Test fun resumeSaveSuccessClearsEditingAndKeepsData() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Success(resume(3))))
        val viewModel = CandidateResumeViewModel(repository); advanceUntilIdle()
        viewModel.edit()
        viewModel.save("Summary", emptyList(), emptyList())
        advanceUntilIdle()
        repository.pending.complete(ApiResult.Success(resume(4)))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.editing)
        assertFalse(viewModel.state.value.submitting)
        assertEquals(4, viewModel.state.value.data?.version)
    }

    @Test fun resumeSaveFailureKeepsEditingState() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Success(resume(3))))
        val viewModel = CandidateResumeViewModel(repository); advanceUntilIdle()
        viewModel.edit()
        viewModel.save("Summary", emptyList(), emptyList())
        advanceUntilIdle()
        repository.pending.complete(ApiResult.Failure("Version conflict"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.editing)
        assertEquals("Version conflict", viewModel.state.value.message)
    }

    @Test fun missingResumeCreatesWithVersionZeroAndRecreationReloads() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Failure("missing", statusCode = 404), ApiResult.Failure("missing", statusCode = 404)))
        val first = CandidateResumeViewModel(repository); advanceUntilIdle()
        assertTrue(first.state.value.notCreated)
        first.save("Summary", emptyList(), emptyList())
        advanceUntilIdle()
        assertEquals(0, repository.lastRequest?.expectedVersion)
        repository.pending.complete(ApiResult.Success(resume(1)))
        advanceUntilIdle(); assertTrue(first.state.value.saved)
        CandidateResumeViewModel(repository); advanceUntilIdle()
        assertEquals(2, repository.getCalls)
    }

    @Test fun resumeValidationAndDuplicateSubmitAreHandled() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Success(resume(3))))
        val viewModel = CandidateResumeViewModel(repository); advanceUntilIdle()
        viewModel.save("", emptyList(), emptyList())
        assertTrue(viewModel.state.value.fieldErrors.keys.contains("summary"))
        viewModel.save("Summary", emptyList(), emptyList())
        viewModel.save("Summary", emptyList(), emptyList())
        advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertEquals(3, repository.lastRequest?.expectedVersion)
    }

    @Test fun resumeValidationProvidesVisibleSafeMessage() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Failure("missing", statusCode = 404)))
        val viewModel = CandidateResumeViewModel(repository)
        advanceUntilIdle()
        viewModel.save("", emptyList(), emptyList())
        assertEquals("Please correct the highlighted fields.", viewModel.state.value.message)
        assertTrue(viewModel.state.value.fieldErrors.keys.contains("summary"))
        assertEquals(0, repository.saveCalls)
    }

    @Test fun resumeSaveNormalizesSkills() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Success(resume(3))))
        val viewModel = CandidateResumeViewModel(repository); advanceUntilIdle()
        viewModel.save("Summary", listOf(" Java ", "java", ""), emptyList())
        advanceUntilIdle()
        assertEquals(listOf("Java", "java"), repository.lastRequest?.skills)
    }

    @Test fun resumeValidationRejectsTooLongSkill() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Success(resume(3))))
        val viewModel = CandidateResumeViewModel(repository); advanceUntilIdle()
        viewModel.save("Summary", listOf("Java", "x".repeat(201)), emptyList())
        assertEquals("Each skill must be at most 200 characters", viewModel.state.value.fieldErrors["skills"])
        assertEquals(0, repository.saveCalls)
    }

    @Test fun jobPreferencesUseCurrentVersionAndNormalizeLists() = runTest(main.dispatcher) {
        val repository = PreferenceFake()
        val viewModel = JobPreferenceViewModel(repository)
        advanceUntilIdle()

        viewModel.save(
            listOf(" Backend Engineer ", "Java Developer", "Backend Engineer"),
            listOf(" Singapore ", "Singapore"),
            setOf(WorkplaceType.HYBRID), setOf(EmploymentType.FULL_TIME), 5000L)
        advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertEquals(4, repository.lastRequest?.expectedVersion)
        assertEquals(listOf("Backend Engineer", "Java Developer"),
            repository.lastRequest?.desiredTitles)
        assertEquals(listOf("Singapore"), repository.lastRequest?.preferredLocations)
        assertEquals(5000L, repository.lastRequest?.minimumSalary)

        repository.pending.complete(ApiResult.Success(preference(version = 5)))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saved)
        assertEquals(5, viewModel.state.value.data?.version)
    }

    @Test fun jobPreferencesRejectTooManyAndTooLongTitles() = runTest(main.dispatcher) {
        val repository = PreferenceFake()
        val viewModel = JobPreferenceViewModel(repository)
        advanceUntilIdle()

        viewModel.save((1..21).map { "Title $it" }, emptyList(), emptySet(), emptySet(), null)
        assertEquals("Use at most 20 titles", viewModel.state.value.fieldErrors["desiredTitles"])
        assertEquals(0, repository.saveCalls)

        viewModel.save(listOf("x".repeat(201)), emptyList(), emptySet(), emptySet(), null)
        assertEquals("Each title must be at most 200 characters",
            viewModel.state.value.fieldErrors["desiredTitles"])
        assertEquals(0, repository.saveCalls)
    }

    @Test fun jobPreferencesNullSalaryStaysNull() = runTest(main.dispatcher) {
        val repository = PreferenceFake()
        val viewModel = JobPreferenceViewModel(repository)
        advanceUntilIdle()

        viewModel.save(listOf("Engineer"), listOf("Singapore"), emptySet(), emptySet(), null)
        advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertNull(repository.lastRequest?.minimumSalary)
    }

    @Test fun avatarSelectAndCancelManagePending() = runTest(main.dispatcher) {
        val viewModel = CandidateProfileViewModel(ProfileFake(), AvatarFake())
        advanceUntilIdle()
        viewModel.selectAvatar(pendingAvatar())
        assertNotNull(viewModel.state.value.avatar.pending)
        assertEquals("image/png", viewModel.state.value.avatar.pending?.contentType)
        viewModel.cancelAvatar()
        assertNull(viewModel.state.value.avatar.pending)
    }

    @Test fun avatarUploadRejectsUnsupportedType() = runTest(main.dispatcher) {
        val avatar = AvatarFake()
        val viewModel = CandidateProfileViewModel(ProfileFake(), avatar)
        advanceUntilIdle()
        viewModel.selectAvatar(pendingAvatar("image/gif"))
        viewModel.uploadAvatar()
        assertEquals("Only PNG or JPEG images are supported.", viewModel.state.value.avatar.message)
        assertEquals(0, avatar.uploadCalls)
        assertNotNull(viewModel.state.value.avatar.pending)
    }

    @Test fun avatarUploadRejectsOversizedImage() = runTest(main.dispatcher) {
        val avatar = AvatarFake()
        val viewModel = CandidateProfileViewModel(ProfileFake(), avatar)
        advanceUntilIdle()
        viewModel.selectAvatar(pendingAvatar("image/png", 6 * 1024 * 1024))
        viewModel.uploadAvatar()
        assertEquals("This image is larger than 5 MB.", viewModel.state.value.avatar.message)
        assertEquals(0, avatar.uploadCalls)
    }

    @Test fun avatarUploadSuccessReflectsUrlAndBumpsRevision() = runTest(main.dispatcher) {
        val avatar = AvatarFake(uploadResult = ApiResult.Success(avatarMetadata()))
        val viewModel = CandidateProfileViewModel(ProfileFake(), avatar)
        advanceUntilIdle()
        viewModel.selectAvatar(pendingAvatar())
        viewModel.uploadAvatar()
        advanceUntilIdle()
        assertEquals(1, avatar.uploadCalls)
        assertEquals("image/png", avatar.lastUpload?.contentType)
        assertEquals("/api/v1/avatars/u", viewModel.state.value.data?.avatarUrl)
        assertNull(viewModel.state.value.avatar.pending)
        assertEquals(1L, viewModel.state.value.avatar.revision)
        assertFalse(viewModel.state.value.avatar.uploading)
    }

    @Test fun avatarUploadFailureKeepsPendingAndShowsMessage() = runTest(main.dispatcher) {
        val avatar = AvatarFake(uploadResult = ApiResult.Failure("Unable to upload your avatar."))
        val viewModel = CandidateProfileViewModel(ProfileFake(), avatar)
        advanceUntilIdle()
        viewModel.selectAvatar(pendingAvatar())
        viewModel.uploadAvatar()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.avatar.pending)
        assertEquals("Unable to upload your avatar.", viewModel.state.value.avatar.message)
        assertFalse(viewModel.state.value.avatar.uploading)
    }

    @Test fun avatarDeleteSuccessClearsUrlAndBumpsRevision() = runTest(main.dispatcher) {
        val avatar = AvatarFake()
        val viewModel = CandidateProfileViewModel(
            ProfileFake(getResult = ApiResult.Success(profile(avatarUrl = "/api/v1/avatars/u"))),
            avatar,
        )
        advanceUntilIdle()
        viewModel.deleteAvatar()
        advanceUntilIdle()
        assertEquals(1, avatar.deleteCalls)
        assertNull(viewModel.state.value.data?.avatarUrl)
        assertEquals(1L, viewModel.state.value.avatar.revision)
        assertFalse(viewModel.state.value.avatar.deleting)
    }

    @Test fun avatarDeleteFailureKeepsUrlAndShowsMessage() = runTest(main.dispatcher) {
        val avatar = AvatarFake(deleteResult = ApiResult.Failure("Unable to remove your avatar."))
        val viewModel = CandidateProfileViewModel(
            ProfileFake(getResult = ApiResult.Success(profile(avatarUrl = "/api/v1/avatars/u"))),
            avatar,
        )
        advanceUntilIdle()
        viewModel.deleteAvatar()
        advanceUntilIdle()
        assertEquals("/api/v1/avatars/u", viewModel.state.value.data?.avatarUrl)
        assertEquals("Unable to remove your avatar.", viewModel.state.value.avatar.message)
        assertEquals(0L, viewModel.state.value.avatar.revision)
    }

    @Test fun avatarTooLargeShowsSafeErrorAndClearsPending() = runTest(main.dispatcher) {
        val viewModel = CandidateProfileViewModel(ProfileFake(), AvatarFake())
        advanceUntilIdle()
        viewModel.selectAvatar(pendingAvatar())
        assertNotNull(viewModel.state.value.avatar.pending)
        viewModel.rejectAvatarTooLarge()
        assertNull(viewModel.state.value.avatar.pending)
        assertEquals("This image is larger than 5 MB.", viewModel.state.value.avatar.message)
    }

    private fun profile(name:String="Candidate",version:Int=1,avatarUrl:String?=null,gender:Gender?=null,phone:String?=null,birthplace:String?=null,age:Int?=null)=CandidateProfileDto("u",name,"candidate@example.com","",avatarUrl,"",age,gender,phone,birthplace,CandidateStats(0,0,0,0),version,"2026-08-11T08:00:00Z","2026-08-11T08:00:00Z")
    private fun resume(version:Int)=Resume("r","Candidate",27,"Singapore","Engineer","Summary",emptyList(),version,"2026-08-11T08:00:00Z","2026-08-11T08:00:00Z")
    private fun avatarMetadata(url:String="/api/v1/avatars/u")=AvatarMetadata("u",url,"image/png",42L,"2026-08-16T08:00:00Z")
    private fun pendingAvatar(contentType:String="image/png",size:Int=4)=PendingAvatar(if(contentType=="image/png")"avatar.png" else "avatar.jpg",contentType,ByteArray(size))
    private fun preference(version:Int=4)=JobPreference(
        listOf("Backend Engineer"), listOf("Singapore"), listOf(WorkplaceType.HYBRID),
        listOf(EmploymentType.FULL_TIME), 5000, "SGD", "MONTH", version,
        "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z")

    private inner class ProfileFake(private val getResult:ApiResult<CandidateProfileDto> = ApiResult.Success(profile())):CandidateProfileRepository{
        var saveCalls=0;var lastRequest:UpdateProfileRequest?=null;val pending=CompletableDeferred<ApiResult<CandidateProfileDto>>()
        override suspend fun get()=getResult
        override suspend fun update(request:UpdateProfileRequest):ApiResult<CandidateProfileDto>{saveCalls++;lastRequest=request;return pending.await()}
    }
    private inner class ResumeFake(private val gets:MutableList<ApiResult<Resume>>):CandidateResumeRepository{
        var getCalls=0;var saveCalls=0;var lastRequest:SaveResumeRequest?=null;val pending=CompletableDeferred<ApiResult<Resume>>()
        override suspend fun get():ApiResult<Resume>{getCalls++;return gets.removeFirst()}
        override suspend fun save(request:SaveResumeRequest):ApiResult<Resume>{saveCalls++;lastRequest=request;return pending.await()}
    }
    private inner class AvatarFake(
        var uploadResult:ApiResult<AvatarMetadata> = ApiResult.Success(avatarMetadata()),
        var deleteResult:ApiResult<Unit> = ApiResult.Success(Unit),
    ):CandidateAvatarRepository{
        var uploadCalls=0;var deleteCalls=0;var lastUpload:AvatarUpload?=null
        override suspend fun upload(request:AvatarUpload):ApiResult<AvatarMetadata>{uploadCalls++;lastUpload=request;return uploadResult}
        override suspend fun delete():ApiResult<Unit>{deleteCalls++;return deleteResult}
    }
    private inner class PreferenceFake:CandidateRecommendationRepository {
        var saveCalls=0;var lastRequest:SaveJobPreferenceRequest?=null
        val pending=CompletableDeferred<ApiResult<JobPreference>>()
        override suspend fun preferences():ApiResult<JobPreference> = ApiResult.Success(preference())
        override suspend fun savePreferences(request:SaveJobPreferenceRequest):ApiResult<JobPreference>{
            saveCalls++;lastRequest=request;return pending.await()
        }
    }
}
