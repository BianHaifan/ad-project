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
        val viewModel = CandidateProfileViewModel(repository)
        advanceUntilIdle()
        assertEquals("Candidate", viewModel.state.value.data?.fullName)
        viewModel.edit()
        viewModel.save("Updated", "Engineer", "Singapore")
        viewModel.save("Duplicate", "Engineer", "Singapore")
        advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        repository.pending.complete(ApiResult.Success(profile("Updated", 2)))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saved)
        assertEquals("Updated", viewModel.state.value.data?.fullName)
    }

    @Test fun profileValidationAndRetryErrorAreSafe() = runTest(main.dispatcher) {
        val failing = ProfileFake(getResult = ApiResult.Failure("Safe profile error"))
        val viewModel = CandidateProfileViewModel(failing)
        advanceUntilIdle()
        assertEquals("Safe profile error", viewModel.state.value.message)
        val valid = CandidateProfileViewModel(ProfileFake())
        advanceUntilIdle(); valid.edit(); valid.save("", "", "")
        assertEquals("Full name is required", valid.state.value.fieldErrors["fullName"])
    }

    @Test fun missingResumeCreatesWithVersionZeroAndRecreationReloads() = runTest(main.dispatcher) {
        val repository = ResumeFake(mutableListOf(ApiResult.Failure("missing", statusCode = 404), ApiResult.Failure("missing", statusCode = 404)))
        val first = CandidateResumeViewModel(repository); advanceUntilIdle()
        assertTrue(first.state.value.notCreated)
        first.save("Candidate", "27", "Singapore", "Engineer", "Summary", emptyList())
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
        viewModel.save("Candidate", "bad", "", "", "", emptyList())
        assertTrue(viewModel.state.value.fieldErrors.keys.containsAll(listOf("age", "location", "headline", "summary")))
        viewModel.save("Candidate", "27", "Singapore", "Engineer", "Summary", emptyList())
        viewModel.save("Candidate", "28", "Singapore", "Engineer", "Summary", emptyList())
        advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
        assertEquals(3, repository.lastRequest?.expectedVersion)
    }

    private fun profile(name:String="Candidate",version:Int=1)=CandidateProfileDto("u",name,"candidate@example.com","",null,"",CandidateStats(0,0,0,0),version,"2026-08-11T08:00:00Z","2026-08-11T08:00:00Z")
    private fun resume(version:Int)=Resume("r","Candidate",27,"Singapore","Engineer","Summary",emptyList(),version,"2026-08-11T08:00:00Z","2026-08-11T08:00:00Z")

    private inner class ProfileFake(private val getResult:ApiResult<CandidateProfileDto> = ApiResult.Success(profile())):CandidateProfileRepository{
        var saveCalls=0;val pending=CompletableDeferred<ApiResult<CandidateProfileDto>>()
        override suspend fun get()=getResult
        override suspend fun update(request:UpdateProfileRequest):ApiResult<CandidateProfileDto>{saveCalls++;return pending.await()}
    }
    private inner class ResumeFake(private val gets:MutableList<ApiResult<Resume>>):CandidateResumeRepository{
        var getCalls=0;var saveCalls=0;var lastRequest:SaveResumeRequest?=null;val pending=CompletableDeferred<ApiResult<Resume>>()
        override suspend fun get():ApiResult<Resume>{getCalls++;return gets.removeFirst()}
        override suspend fun save(request:SaveResumeRequest):ApiResult<Resume>{saveCalls++;lastRequest=request;return pending.await()}
    }
}
