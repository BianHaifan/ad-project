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
import com.adproject.candidate.data.contract.MatchAnalysis
import com.adproject.candidate.data.contract.RecommendationEnvelope
import com.adproject.candidate.data.contract.RecommendationMeta
import com.adproject.candidate.data.contract.RecommendedJob
import com.adproject.candidate.data.contract.Salary
import com.adproject.candidate.data.contract.Visibility
import com.adproject.candidate.data.contract.WorkplaceType
import com.adproject.candidate.feature.auth.AuthViewModel
import com.adproject.candidate.feature.jobs.JobDetailViewModel
import com.adproject.candidate.feature.jobs.JobFeedViewModel
import com.adproject.candidate.feature.jobs.SavedJobsViewModel
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

    @Test fun registerFailureClearsOnEditAndAllowsRetry() = runTest(main.dispatcher) {
        val auth = QueuedAuthRepository(
            ApiResult.Failure("Email already registered", mapOf("email" to "already registered")),
            ApiResult.Success(Unit),
        )
        val viewModel = AuthViewModel(auth)
        viewModel.updateFullName("Candidate")
        viewModel.updateRegisterEmail("taken@example.com")
        viewModel.updateRegisterPassword("UnitOnly9!")
        viewModel.updateConfirmPassword("UnitOnly9!")
        viewModel.updateAgreed(true)
        viewModel.register()
        advanceUntilIdle()
        assertEquals(1, auth.registerCalls)
        assertEquals("Email already registered", viewModel.register.value.message)
        assertEquals("already registered", viewModel.register.value.fieldErrors["email"])
        assertFalse(viewModel.register.value.submitting)

        viewModel.updateRegisterEmail("fresh@example.com")
        assertNull(viewModel.register.value.message)
        assertFalse(viewModel.register.value.fieldErrors.containsKey("email"))
        viewModel.register()
        advanceUntilIdle()
        assertEquals(2, auth.registerCalls)
        assertNull(viewModel.register.value.message)
        assertTrue(viewModel.register.value.fieldErrors.isEmpty())
    }

    @Test fun jobFeedCoversContentEmptyErrorRetryAndRefresh() = runTest(main.dispatcher) {
        val repository = QueueJobRepository(
            ApiResult.Success(CandidateJobPage(listOf(candidateJob()), PageMeta(1, 20, 1, false))),
            ApiResult.Success(CandidateJobPage(emptyList(), PageMeta(1, 20, 0, false))),
            ApiResult.Failure("Network unavailable"),
            ApiResult.Success(CandidateJobPage(listOf(candidateJob("job-2")), PageMeta(1, 20, 1, false))),
        )
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        assertEquals("job-1", viewModel.state.value.data?.jobs?.first()?.jobId)
        assertEquals(0, viewModel.state.value.data?.jobs?.first()?.match)
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
            ApiResult.Success(CandidateJobPage(listOf(candidateJob()), PageMeta(1, 20, 1, false))),
            ApiResult.Success(CandidateJobPage(listOf(candidateJob()), PageMeta(1, 20, 1, false))),
        )
        JobFeedViewModel(repository)
        advanceUntilIdle()
        JobFeedViewModel(repository)
        advanceUntilIdle()
        assertEquals(2, repository.jobCalls)
    }

    @Test fun detailCoversSuccess404AndNoFakeMatchOrSave() = runTest(main.dispatcher) {
        val success = QueueJobRepository(detailResults = mutableListOf(ApiResult.Success(
            CandidateJobDetail(candidateJob(), null, CandidateJobApplicationState.NOT_APPLIED, false),
        )))
        val viewModel = JobDetailViewModel("job-1", success)
        advanceUntilIdle()
        assertEquals("job-1", viewModel.state.value.data?.job?.jobId)
        assertFalse(viewModel.state.value.data!!.matchAnalysisAvailable)
        assertNull(viewModel.state.value.data!!.job.match)
        assertEquals(CandidateJobApplicationState.NOT_APPLIED, viewModel.state.value.data!!.applicationState)

        val missing = QueueJobRepository(detailResults = mutableListOf(ApiResult.Failure("gone", statusCode = 404)))
        val missingViewModel = JobDetailViewModel("missing", missing)
        advanceUntilIdle()
        assertTrue(missingViewModel.state.value.notFound)
        assertNull(missingViewModel.state.value.data)
    }

    @Test fun firstLoadRequestsPageOneWithDefaultSize() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repository.enqueue(envelope(listOf(rec("a")), 1, 10, 1, false))
        JobFeedViewModel(repository)
        advanceUntilIdle()
        val call = repository.calls.single()
        assertEquals(1, call.page)
        assertEquals(10, call.pageSize)
        assertEquals("", call.q)
        assertNull(call.employmentType)
        assertNull(call.workplaceType)
        assertNull(call.location)
        assertNull(call.minimumSalary)
    }

    @Test fun recommendationFeedPaginatesAppendsAndDeduplicates() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repository.enqueue(envelope(listOf(rec("a"), rec("b")), 1, 2, 4, true))
        repository.enqueue(envelope(listOf(rec("b"), rec("c")), 2, 2, 4, true))
        repository.enqueue(envelope(listOf(rec("d")), 3, 2, 4, false))
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), viewModel.state.value.data?.jobs?.map { it.jobId })
        assertTrue(viewModel.state.value.hasNext)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), viewModel.state.value.data?.jobs?.map { it.jobId })

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c", "d"), viewModel.state.value.data?.jobs?.map { it.jobId })
        assertFalse(viewModel.state.value.hasNext)
    }

    @Test fun employmentTypeFilterResetsToFirstPage() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repository.enqueue(envelope(listOf(rec("a")), 1, 10, 11, true))
        repository.enqueue(envelope(listOf(rec("b")), 2, 10, 11, true))
        repository.enqueue(envelope(listOf(rec("intern")), 1, 10, 1, false))
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.page)

        viewModel.selectEmploymentType(EmploymentType.INTERNSHIP)
        advanceUntilIdle()
        assertEquals(1, repository.calls.last().page)
        assertEquals(EmploymentType.INTERNSHIP, repository.calls.last().employmentType)
        assertEquals(listOf("intern"), viewModel.state.value.data?.jobs?.map { it.jobId })
    }

    @Test fun loadMoreFailureKeepsJobsAndShowsBottomRetry() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repository.enqueue(envelope(listOf(rec("a")), 1, 10, 2, true))
        repository.enqueue(ApiResult.Failure("Network unavailable"))
        repository.enqueue(envelope(listOf(rec("b")), 2, 10, 2, false))
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("a"), viewModel.state.value.data?.jobs?.map { it.jobId })
        assertTrue(viewModel.state.value.loadMoreError)
        assertFalse(viewModel.state.value.loadingMore)

        viewModel.retryLoadMore()
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), viewModel.state.value.data?.jobs?.map { it.jobId })
        assertFalse(viewModel.state.value.loadMoreError)
    }

    @Test fun tailPageDoesNotRequestFurtherPages() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repository.enqueue(envelope(listOf(rec("a")), 1, 10, 1, false))
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(1, repository.calls.size)
        assertEquals(1, viewModel.state.value.page)
    }

    @Test fun jobFiltersResetToFirstPageAndAreSent() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repository.enqueue(envelope(listOf(rec("a")), 1, 10, 11, true))
        repository.enqueue(envelope(listOf(rec("b")), 2, 10, 11, true))
        repository.enqueue(envelope(listOf(rec("remote")), 1, 10, 1, false))
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.page)

        viewModel.selectWorkplaceType(WorkplaceType.REMOTE)
        advanceUntilIdle()
        val call = repository.calls.last()
        assertEquals(1, call.page)
        assertEquals(WorkplaceType.REMOTE, call.workplaceType)
        assertEquals(listOf("remote"), viewModel.state.value.data?.jobs?.map { it.jobId })
    }

    @Test fun clearFiltersResetsAllFiltersAndReloads() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repeat(4) { repository.enqueue(envelope(listOf(rec("x")), 1, 10, 1, false)) }
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        viewModel.selectEmploymentType(EmploymentType.INTERNSHIP)
        viewModel.selectLocation("Singapore")
        viewModel.clearFilters()
        advanceUntilIdle()
        val call = repository.calls.last()
        assertNull(call.employmentType)
        assertNull(call.workplaceType)
        assertNull(call.location)
        assertNull(call.minimumSalary)
        assertEquals(1, call.page)
    }

    @Test fun saveFailureRollsBackOptimisticSave() = runTest(main.dispatcher) {
        val repository = PagedRecommendationRepository()
        repository.enqueue(envelope(listOf(rec("a")), 1, 10, 1, false))
        repository.enqueueSave(ApiResult.Failure("Unable to save this job."))
        val viewModel = JobFeedViewModel(repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.data!!.jobs.first().isSaved)

        viewModel.toggleSave("a")
        assertTrue(viewModel.state.value.data!!.jobs.first().isSaved)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.data!!.jobs.first().isSaved)
        assertEquals("Unable to save this job.", viewModel.state.value.saveError)
        assertEquals(listOf("a" to true), repository.saveCalls)
    }

    @Test fun savedJobsUnsaveSuccessRemovesJob() = runTest(main.dispatcher) {
        val repository = SavedJobsRepository()
        val viewModel = SavedJobsViewModel(repository)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.jobs.size)

        viewModel.unsave("job-1")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.jobs.isEmpty())
        assertEquals(listOf("job-1"), repository.unsaveCalls)
    }

    @Test fun savedJobsUnsaveFailureRestoresJob() = runTest(main.dispatcher) {
        val repository = SavedJobsRepository()
        repository.unsaveResult = ApiResult.Failure("Unable to remove this job.")
        val viewModel = SavedJobsViewModel(repository)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.jobs.size)

        viewModel.unsave("job-1")
        assertTrue(viewModel.state.value.jobs.isEmpty())
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.jobs.size)
        assertEquals("Unable to remove this job.", viewModel.state.value.saveError)
    }
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

private class QueuedAuthRepository(vararg results: ApiResult<Unit>) : AuthRepository {
    var loginCalls = 0
    var registerCalls = 0
    private val queue = results.toMutableList()
    override suspend fun login(email: String, password: String): ApiResult<Unit> { loginCalls++; return queue.removeFirst() }
    override suspend fun register(fullName: String, email: String, password: String): ApiResult<Unit> { registerCalls++; return queue.removeFirst() }
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
    override suspend fun recommendations(
        q: String?,
        employmentType: EmploymentType?,
        workplaceType: WorkplaceType?,
        location: String?,
        minimumSalary: Long?,
        page: Int,
        pageSize: Int,
    ): ApiResult<RecommendationEnvelope> {
        jobCalls++
        return when (val result = pageResults.removeFirst()) {
            is ApiResult.Success -> ApiResult.Success(RecommendationEnvelope(
                data = result.value.jobs.mapIndexed { index, job -> RecommendedJob(
                    job.jobId, job.title, job.company.companyId, job.company.name, job.location,
                    job.employmentType, job.workplaceType, job.salary.min, job.salary.max,
                    job.salary.currency, job.salary.period, job.description, job.skills,
                    job.matchScore ?: 0, index + 1, MatchAnalysis(),
                ) },
                meta = RecommendationMeta("MODEL", "test-model", "test-features", "ACTIVE", 1,
                    "2026-08-11T08:00:00Z", page, pageSize, result.value.jobs.size, false),
            ))
            is ApiResult.Failure -> result
        }
    }
    override suspend fun saveJob(jobId: String): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun unsaveJob(jobId: String): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun savedJobs(page: Int, pageSize: Int): ApiResult<CandidateJobPage> =
        ApiResult.Success(CandidateJobPage(emptyList(), PageMeta(1, 20, 0, false)))
    override suspend fun job(jobId: String): ApiResult<CandidateJobDetail> = detailResults.removeFirst()
}

private fun rec(id: String) = RecommendedJob(
    id, "Job $id", "company-$id", "Company $id", "Singapore", EmploymentType.FULL_TIME,
    WorkplaceType.HYBRID, 5000, 8000, "SGD", "MONTH", "Description", emptyList(), 90, 1, MatchAnalysis(),
)

private fun candidateJob(id: String = "job-1") = CandidateJob(
    id, "Backend Engineer", Company("company-1", "Real Company", null, null, null, "APPROVED", null,
        null, null, 1, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z"),
    EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Singapore", Salary(5000, 8000, "SGD", "MONTH"),
    "Description", listOf("Reliable APIs"), listOf("Java"), null, Visibility.PUBLIC, JobStatus.ACTIVE,
    "2026-08-11T08:00:00Z", 2, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z", null, null,
)

private fun envelope(
    jobs: List<RecommendedJob>,
    page: Int,
    pageSize: Int,
    total: Int,
    hasNext: Boolean,
) = ApiResult.Success(RecommendationEnvelope(
    data = jobs,
    meta = RecommendationMeta("MODEL", "test-model", "test-features", "ACTIVE", 1,
        "2026-08-11T08:00:00Z", page, pageSize, total, hasNext),
))

private data class RecCall(
    val q: String?,
    val employmentType: EmploymentType?,
    val workplaceType: WorkplaceType?,
    val location: String?,
    val minimumSalary: Long?,
    val page: Int,
    val pageSize: Int,
)

private class PagedRecommendationRepository : CandidateJobRepository {
    val calls = mutableListOf<RecCall>()
    val saveCalls = mutableListOf<Pair<String, Boolean>>()
    private val results = mutableListOf<ApiResult<RecommendationEnvelope>>()
    private val saveResults = mutableListOf<ApiResult<Unit>>()

    fun enqueue(result: ApiResult<RecommendationEnvelope>) { results.add(result) }
    fun enqueueSave(result: ApiResult<Unit>) { saveResults.add(result) }

    override suspend fun recommendations(
        q: String?,
        employmentType: EmploymentType?,
        workplaceType: WorkplaceType?,
        location: String?,
        minimumSalary: Long?,
        page: Int,
        pageSize: Int,
    ): ApiResult<RecommendationEnvelope> {
        calls.add(RecCall(q, employmentType, workplaceType, location, minimumSalary, page, pageSize))
        return results.removeFirst()
    }

    override suspend fun saveJob(jobId: String): ApiResult<Unit> {
        saveCalls += jobId to true
        return saveResults.removeFirst()
    }

    override suspend fun unsaveJob(jobId: String): ApiResult<Unit> {
        saveCalls += jobId to false
        return saveResults.removeFirst()
    }

    override suspend fun savedJobs(page: Int, pageSize: Int): ApiResult<CandidateJobPage> =
        ApiResult.Failure("unused")

    override suspend fun jobs(q: String?, employmentType: EmploymentType?): ApiResult<CandidateJobPage> =
        ApiResult.Failure("unused")

    override suspend fun job(jobId: String): ApiResult<CandidateJobDetail> =
        ApiResult.Failure("unused")
}

private class SavedJobsRepository : CandidateJobRepository {
    val unsaveCalls = mutableListOf<String>()
    var unsaveResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun savedJobs(page: Int, pageSize: Int): ApiResult<CandidateJobPage> =
        ApiResult.Success(CandidateJobPage(listOf(candidateJob()), PageMeta(1, 20, 1, false)))

    override suspend fun unsaveJob(jobId: String): ApiResult<Unit> {
        unsaveCalls += jobId
        return unsaveResult
    }

    override suspend fun saveJob(jobId: String): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun jobs(q: String?, employmentType: EmploymentType?): ApiResult<CandidateJobPage> =
        ApiResult.Failure("unused")

    override suspend fun recommendations(
        q: String?,
        employmentType: EmploymentType?,
        workplaceType: WorkplaceType?,
        location: String?,
        minimumSalary: Long?,
        page: Int,
        pageSize: Int,
    ): ApiResult<RecommendationEnvelope> = ApiResult.Failure("unused")

    override suspend fun job(jobId: String): ApiResult<CandidateJobDetail> = ApiResult.Failure("unused")
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
