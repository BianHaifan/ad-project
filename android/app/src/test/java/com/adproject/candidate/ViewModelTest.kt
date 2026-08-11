package com.adproject.candidate

import androidx.lifecycle.ViewModel
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AuthRepository
import com.adproject.candidate.data.api.CandidateJobPage
import com.adproject.candidate.data.api.CandidateJobRepository
import com.adproject.candidate.data.contract.CandidateJob
import com.adproject.candidate.data.contract.CandidateJobApplicationState
import com.adproject.candidate.data.contract.CandidateJobDetail
import com.adproject.candidate.data.contract.Company
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.JobStatus
import com.adproject.candidate.data.contract.PageMeta
import com.adproject.candidate.data.contract.Salary
import com.adproject.candidate.data.contract.Visibility
import com.adproject.candidate.data.contract.WorkplaceType
import com.adproject.candidate.feature.auth.AuthViewModel
import com.adproject.candidate.feature.jobs.JobDetailViewModel
import com.adproject.candidate.feature.jobs.JobFeedViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun loginAndRegistrationCannotSubmitTwiceWhileLoading() = runTest(main.dispatcher) {
        val auth = ControllableAuthRepository()
        val viewModel = AuthViewModel(auth)
        viewModel.updateSignInEmail("candidate@example.com")
        viewModel.updateSignInPassword("UnitOnly9!")
        viewModel.signIn()
        viewModel.signIn()
        advanceUntilIdle()
        assertEquals(1, auth.loginCalls)
        assertTrue(viewModel.signIn.value.submitting)
        auth.loginResult.complete(ApiResult.Success(Unit))
        advanceUntilIdle()

        viewModel.updateFullName("Candidate")
        viewModel.updateRegisterEmail("new@example.com")
        viewModel.updateRegisterPassword("UnitOnly9!")
        viewModel.updateConfirmPassword("UnitOnly9!")
        viewModel.updateAgreed(true)
        viewModel.register()
        viewModel.register()
        advanceUntilIdle()
        assertEquals(1, auth.registerCalls)
    }

    @Test fun authValidationAndSafeFailuresReachUiState() = runTest(main.dispatcher) {
        val auth = ControllableAuthRepository()
        val viewModel = AuthViewModel(auth)
        viewModel.signIn()
        assertTrue(viewModel.signIn.value.fieldErrors.keys.containsAll(listOf("email", "password")))
        viewModel.updateSignInEmail("candidate@example.com")
        viewModel.updateSignInPassword("UnitOnly9!")
        viewModel.signIn()
        advanceUntilIdle()
        auth.loginResult.complete(ApiResult.Failure("Unable to sign in safely", mapOf("email" to "invalid"), 401))
        advanceUntilIdle()
        assertEquals("Unable to sign in safely", viewModel.signIn.value.message)
        assertEquals("invalid", viewModel.signIn.value.fieldErrors["email"])
        assertFalse(viewModel.signIn.value.submitting)
    }

    @Test fun jobFeedCoversContentEmptyErrorRetryAndRefresh() = runTest(main.dispatcher) {
        val repository = QueueJobRepository(
            ApiResult.Success(CandidateJobPage(listOf(job()), PageMeta(1, 20, 1, false))),
            ApiResult.Success(CandidateJobPage(emptyList(), PageMeta(1, 20, 0, false))),
            ApiResult.Failure("Network unavailable"),
            ApiResult.Success(CandidateJobPage(listOf(job("job-2")), PageMeta(1, 20, 1, false))),
        )
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        assertEquals("job-1", viewModel.state.value.data?.jobs?.first()?.jobId)
        assertNull(viewModel.state.value.data?.jobs?.first()?.match)
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.data?.jobs.isNullOrEmpty())
        viewModel.retry()
        advanceUntilIdle()
        assertEquals("Network unavailable", viewModel.state.value.message)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals("job-2", viewModel.state.value.data?.jobs?.first()?.jobId)
    }

    @Test fun newJobFeedViewModelRequestsBackendAgainAfterRecreation() = runTest(main.dispatcher) {
        val repository = QueueJobRepository(
            ApiResult.Success(CandidateJobPage(listOf(job()), PageMeta(1, 20, 1, false))),
            ApiResult.Success(CandidateJobPage(listOf(job()), PageMeta(1, 20, 1, false))),
        )
        JobFeedViewModel(repository)
        advanceUntilIdle()
        JobFeedViewModel(repository)
        advanceUntilIdle()
        assertEquals(2, repository.jobCalls)
    }

    @Test fun detailCoversSuccess404AndNoFakeMatchOrSave() = runTest(main.dispatcher) {
        val success = QueueJobRepository(detailResults = mutableListOf(ApiResult.Success(
            CandidateJobDetail(job(), null, CandidateJobApplicationState.NOT_APPLIED, false),
        )))
        val viewModel = JobDetailViewModel("job-1", success)
        advanceUntilIdle()
        assertEquals("job-1", viewModel.state.value.data?.job?.jobId)
        assertFalse(viewModel.state.value.data!!.matchAnalysisAvailable)
        assertNull(viewModel.state.value.data!!.job.match)

        val missing = QueueJobRepository(detailResults = mutableListOf(ApiResult.Failure("gone", statusCode = 404)))
        val missingViewModel = JobDetailViewModel("missing", missing)
        advanceUntilIdle()
        assertTrue(missingViewModel.state.value.notFound)
        assertNull(missingViewModel.state.value.data)
    }

    private fun job(id: String = "job-1") = CandidateJob(
        id, "Backend Engineer", Company("company-1", "Real Company", null, null, null, "APPROVED", null,
            null, null, 1, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z"),
        EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Singapore", Salary(5000, 8000, "SGD", "MONTH"),
        "Description", listOf("Reliable APIs"), listOf("Java"), null, Visibility.PUBLIC, JobStatus.ACTIVE,
        "2026-08-11T08:00:00Z", 2, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z", null, null,
    )
}

private class ControllableAuthRepository : AuthRepository {
    var loginCalls = 0
    var registerCalls = 0
    val loginResult = CompletableDeferred<ApiResult<Unit>>()
    val registerResult = CompletableDeferred<ApiResult<Unit>>()
    override suspend fun login(email: String, password: String): ApiResult<Unit> { loginCalls++; return loginResult.await() }
    override suspend fun register(fullName: String, email: String, password: String): ApiResult<Unit> { registerCalls++; return registerResult.await() }
    override suspend fun logout(): ApiResult<Unit> = ApiResult.Success(Unit)
}

private class QueueJobRepository(
    vararg pages: ApiResult<CandidateJobPage>,
    val detailResults: MutableList<ApiResult<CandidateJobDetail>> = mutableListOf(),
) : CandidateJobRepository {
    private val pageResults = pages.toMutableList()
    var jobCalls = 0
    override suspend fun jobs(q: String?, employmentType: EmploymentType?): ApiResult<CandidateJobPage> {
        jobCalls++
        return pageResults.removeFirst()
    }
    override suspend fun job(jobId: String): ApiResult<CandidateJobDetail> = detailResults.removeFirst()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
