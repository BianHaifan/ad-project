package com.adproject.candidate

import com.adproject.candidate.data.api.*
import com.adproject.candidate.data.contract.*
import com.adproject.candidate.feature.applications.ApplicationDetailViewModel
import com.adproject.candidate.feature.applications.ApplicationListViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationTrackingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun listLoadsRealPageEmptyErrorRetryFilterAndPagination() = runTest(main.dispatcher) {
        val repository = TrackingRepository(listResults = mutableListOf(
            ApiResult.Success(page(listOf(summary("a1")), hasNext = true)),
            ApiResult.Success(page(listOf(summary("a2")), page = 2)),
            ApiResult.Failure("Network unavailable"),
            ApiResult.Success(page(emptyList())),
        ))
        val viewModel = ApplicationListViewModel(repository)
        viewModel.load(); advanceUntilIdle()
        assertEquals(listOf("a1"), viewModel.state.value.applications.map { it.applicationId })
        assertEquals(ApplicationListFilter.ACTIVE, repository.listCalls[0].first)
        assertEquals(ApplicationCounts(1, 2, 3), viewModel.state.value.counts)
        viewModel.loadMore(); advanceUntilIdle()
        assertEquals(listOf("a1", "a2"), viewModel.state.value.applications.map { it.applicationId })
        assertEquals(2, repository.listCalls[1].second)
        viewModel.selectFilter(ApplicationListFilter.ARCHIVED); advanceUntilIdle()
        assertEquals("Network unavailable", viewModel.state.value.message)
        assertTrue(viewModel.state.value.applications.isEmpty())
        viewModel.retry(); advanceUntilIdle()
        assertTrue(viewModel.state.value.applications.isEmpty())
        assertNull(viewModel.state.value.message)
        assertEquals(ApplicationListFilter.ARCHIVED, repository.listCalls.last().first)
    }

    @Test fun refreshAndNewViewModelAlwaysRequestBackend() = runTest(main.dispatcher) {
        val repository = TrackingRepository(listResults = mutableListOf(
            ApiResult.Success(page(listOf(summary("a1")))),
            ApiResult.Success(page(listOf(summary("a2")))),
            ApiResult.Success(page(listOf(summary("a3")))),
        ))
        val first = ApplicationListViewModel(repository)
        first.load(); advanceUntilIdle(); first.refresh(); advanceUntilIdle()
        assertEquals("a2", first.state.value.applications.single().applicationId)
        val recreated = ApplicationListViewModel(repository)
        recreated.load(); advanceUntilIdle()
        assertEquals("a3", recreated.state.value.applications.single().applicationId)
        assertEquals(3, repository.listCalls.size)
    }

    @Test fun detailLoadsSnapshotOrderAndKeepsNullMlAndInterview() = runTest(main.dispatcher) {
        val repository = TrackingRepository(detailResults = mutableListOf(ApiResult.Success(detail())))
        val viewModel = ApplicationDetailViewModel("a1", repository)
        advanceUntilIdle()
        val application = viewModel.state.value.application!!
        assertEquals(listOf("First", "Second"), application.resumeSnapshot.experiences.map { it.title })
        assertNull(application.matchScore)
        assertNull(application.interview)
        assertEquals(listOf("APPLIED"), application.timeline.map { it.status.name })
    }

    @Test fun detail404IsExplicit() = runTest(main.dispatcher) {
        val repository = TrackingRepository(detailResults = mutableListOf(
            ApiResult.Failure("Application unavailable", statusCode = 404)))
        val viewModel = ApplicationDetailViewModel("missing", repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.notFound)
        assertNull(viewModel.state.value.application)
    }

    @Test fun legalWithdrawRequiresConfirmationReasonVersionAndPreventsDuplicate() = runTest(main.dispatcher) {
        val pending = CompletableDeferred<ApiResult<CandidateApplication>>()
        val repository = TrackingRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())), pendingWithdraw = pending)
        val viewModel = ApplicationDetailViewModel("a1", repository)
        advanceUntilIdle()
        viewModel.requestWithdraw()
        assertTrue(viewModel.state.value.confirmingWithdraw)
        viewModel.confirmWithdraw()
        assertEquals("Please provide a reason for withdrawing.", viewModel.state.value.message)
        viewModel.updateWithdrawReason("Changed plans")
        viewModel.confirmWithdraw(); viewModel.confirmWithdraw(); advanceUntilIdle()
        assertEquals(1, repository.withdrawCalls.size)
        assertEquals("a1", repository.withdrawCalls.single().first)
        assertEquals(1, repository.withdrawCalls.single().second.expectedVersion)
        assertEquals("Changed plans", repository.withdrawCalls.single().second.reason)
        pending.complete(ApiResult.Success(detail(ApplicationStatus.WITHDRAWN, version = 2)))
        advanceUntilIdle()
        assertEquals(ApplicationStatus.WITHDRAWN, viewModel.state.value.application?.status)
        assertEquals(2, viewModel.state.value.application?.version)
        assertFalse(viewModel.state.value.confirmingWithdraw)
    }

    @Test fun terminalStatesCannotWithdrawAndFailureNeverChangesLocalState() = runTest(main.dispatcher) {
        assertFalse(ApplicationDetailViewModel.canWithdraw(ApplicationStatus.REJECTED))
        assertFalse(ApplicationDetailViewModel.canWithdraw(ApplicationStatus.WITHDRAWN))
        assertFalse(ApplicationDetailViewModel.canWithdraw(ApplicationStatus.OFFERED))
        assertTrue(ApplicationDetailViewModel.canWithdraw(ApplicationStatus.INTERVIEW))
        val repository = TrackingRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            withdrawResults = mutableListOf(ApiResult.Failure(
                "This application changed. Refresh before trying again.", code = "VERSION_CONFLICT")),
        )
        val viewModel = ApplicationDetailViewModel("a1", repository)
        advanceUntilIdle(); viewModel.requestWithdraw(); viewModel.updateWithdrawReason("Changed plans")
        viewModel.confirmWithdraw(); advanceUntilIdle()
        assertEquals(ApplicationStatus.APPLIED, viewModel.state.value.application?.status)
        assertEquals("This application changed. Refresh before trying again.", viewModel.state.value.message)
    }
}

private class TrackingRepository(
    val listResults: MutableList<ApiResult<CandidateApplicationPage>> = mutableListOf(),
    val detailResults: MutableList<ApiResult<CandidateApplication>> = mutableListOf(),
    val withdrawResults: MutableList<ApiResult<CandidateApplication>> = mutableListOf(),
    val pendingWithdraw: CompletableDeferred<ApiResult<CandidateApplication>>? = null,
) : CandidateApplicationRepository {
    val listCalls = mutableListOf<Pair<ApplicationListFilter?, Int>>()
    val withdrawCalls = mutableListOf<Pair<String, WithdrawApplicationRequest>>()
    override suspend fun applications(filter: ApplicationListFilter?, page: Int, pageSize: Int): ApiResult<CandidateApplicationPage> {
        listCalls += filter to page
        return listResults.removeFirst()
    }
    override suspend fun application(applicationId: String) = detailResults.removeFirst()
    override suspend fun withdraw(applicationId: String, request: WithdrawApplicationRequest): ApiResult<CandidateApplication> {
        withdrawCalls += applicationId to request
        return pendingWithdraw?.await() ?: withdrawResults.removeFirst()
    }
    override suspend fun submit(jobId: String, idempotencyKey: String, request: SubmitApplicationRequest) =
        ApiResult.Failure("Not used")
}

private fun page(applications: List<CandidateApplicationSummary>, page: Int = 1, hasNext: Boolean = false) =
    CandidateApplicationPage(applications, CandidateApplicationListMeta(
        page, 20, applications.size, hasNext, ApplicationCounts(1, 2, 3)))

private fun summary(id: String) = CandidateApplicationSummary(
    id, "job-$id", ApplicationStatus.APPLIED, NOW, NOW, 1, "Backend Engineer", trackingCompany(),
    null, null, listOf(TimelineStep(ApplicationStatus.APPLIED, true, NOW)),
)

private fun detail(status: ApplicationStatus = ApplicationStatus.APPLIED, version: Int = 1) = CandidateApplication(
    "a1", "job-a1", status, NOW, NOW, version, "Backend Engineer", trackingCompany(), null, null,
    listOf(TimelineStep(ApplicationStatus.APPLIED, true, NOW)),
    ResumeSnapshot("snapshot-1", NOW, "resume-1", "Candidate", 27, "Singapore", "Engineer", "Summary",
        listOf(
            Experience("e1", "First", "A", "One", "2024-01", null),
            Experience("e2", "Second", "B", "Two", "2025-01", null),
        ), 1, NOW, NOW),
    null, emptyList(),
)

private fun trackingCompany() = Company("company-1", "Real Company", null, null, null, "APPROVED",
    null, null, null, 1, NOW, NOW)
private const val NOW = "2026-08-11T08:00:00Z"
