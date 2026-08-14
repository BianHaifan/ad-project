package com.adproject.candidate

import com.adproject.candidate.data.api.*
import com.adproject.candidate.data.contract.*
import com.adproject.candidate.feature.applications.ApplicationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun confirmationLoadsRealJobProfileAndResume() = runTest(main.dispatcher) {
        val app = QueueApplicationRepository()
        val viewModel = viewModel(applications = app)
        viewModel.start("job-1")
        advanceUntilIdle()
        assertEquals("Backend Engineer", viewModel.state.value.job?.job?.title)
        assertEquals("candidate@example.com", viewModel.state.value.profile?.email)
        assertEquals("resume-1", viewModel.state.value.resume?.resumeId)
        assertFalse(viewModel.state.value.resumeMissing)
    }

    @Test fun missingResumeCannotSubmitAndIsExplicit() = runTest(main.dispatcher) {
        val app = QueueApplicationRepository()
        val viewModel = viewModel(applications = app,
            resumeResult = ApiResult.Failure("No resume", statusCode = 404))
        viewModel.start("job-1")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.resumeMissing)
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(0, app.calls)
    }

    @Test fun submitUsesRealFieldsValidUuidAndPreventsDuplicateTap() = runTest(main.dispatcher) {
        val pending = CompletableDeferred<ApiResult<CandidateApplication>>()
        val app = QueueApplicationRepository(mutableListOf(), pending)
        val viewModel = viewModel(applications = app)
        viewModel.start("job-1")
        advanceUntilIdle()
        viewModel.setShareProfile(false)
        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(1, app.calls)
        assertNotNull(java.util.UUID.fromString(app.keys.single()))
        assertEquals("resume-1", app.requests.single().resumeId)
        assertEquals("candidate@example.com", app.requests.single().contactEmail)
        assertFalse(app.requests.single().shareProfile)
        pending.complete(ApiResult.Success(application()))
        advanceUntilIdle()
        assertEquals("application-1", viewModel.state.value.result?.applicationId)
    }

    @Test fun failedNetworkRetryReusesSameIdempotencyKeyAndDoesNotShowSuccess() = runTest(main.dispatcher) {
        val app = QueueApplicationRepository(mutableListOf(
            ApiResult.Failure("Network unavailable"), ApiResult.Success(application()),
        ))
        val viewModel = viewModel(applications = app)
        viewModel.start("job-1")
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()
        assertNull(viewModel.state.value.result)
        assertEquals("Network unavailable", viewModel.state.value.message)
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(2, app.calls)
        assertEquals(app.keys[0], app.keys[1])
        assertEquals("application-1", viewModel.state.value.result?.applicationId)
    }

    @Test fun newExplicitFlowCreatesNewKeyAndReloadsBackend() = runTest(main.dispatcher) {
        val app = QueueApplicationRepository(mutableListOf(ApiResult.Success(application()), ApiResult.Success(application())))
        val jobs = FixedJobRepository()
        val viewModel = viewModel(jobs, app)
        viewModel.start("job-1"); advanceUntilIdle(); viewModel.submit(); advanceUntilIdle()
        val first = app.keys.single()
        viewModel.clear(); viewModel.start("job-1"); advanceUntilIdle(); viewModel.submit(); advanceUntilIdle()
        assertNotEquals(first, app.keys.last())
        assertEquals(2, jobs.calls)
    }

    private fun viewModel(
        jobs: FixedJobRepository = FixedJobRepository(),
        applications: QueueApplicationRepository,
        resumeResult: ApiResult<Resume> = ApiResult.Success(resume()),
    ) = ApplicationViewModel(jobs, FixedProfileRepository(), FixedResumeRepository(resumeResult), applications)
}

private class FixedJobRepository : CandidateJobRepository {
    var calls = 0
    override suspend fun jobs(q: String?, employmentType: EmploymentType?) =
        ApiResult.Success(CandidateJobPage(emptyList(), PageMeta(1, 20, 0, false)))
    override suspend fun job(jobId: String): ApiResult<CandidateJobDetail> {
        calls++
        return ApiResult.Success(CandidateJobDetail(candidateJob(), null, CandidateJobApplicationState.NOT_APPLIED, false))
    }
}
private class FixedProfileRepository : CandidateProfileRepository {
    override suspend fun get() = ApiResult.Success(profile())
    override suspend fun update(request: UpdateProfileRequest) = ApiResult.Success(profile())
}
private class FixedResumeRepository(private val result: ApiResult<Resume>) : CandidateResumeRepository {
    override suspend fun get() = result
    override suspend fun save(request: SaveResumeRequest) = result
}
private class QueueApplicationRepository(
    private val results: MutableList<ApiResult<CandidateApplication>> = mutableListOf(),
    private val pending: CompletableDeferred<ApiResult<CandidateApplication>>? = null,
) : CandidateApplicationRepository {
    var calls = 0; val keys = mutableListOf<String>(); val requests = mutableListOf<SubmitApplicationRequest>()
    override suspend fun applications(filter: ApplicationListFilter?, page: Int, pageSize: Int) =
        ApiResult.Failure("Not used")
    override suspend fun application(applicationId: String) = ApiResult.Failure("Not used")
    override suspend fun withdraw(applicationId: String, request: WithdrawApplicationRequest) =
        ApiResult.Failure("Not used")
    override suspend fun submit(jobId: String, idempotencyKey: String, request: SubmitApplicationRequest): ApiResult<CandidateApplication> {
        calls++; keys += idempotencyKey; requests += request
        return pending?.await() ?: results.removeFirst()
    }
}

private fun candidateJob() = CandidateJob("job-1", "Backend Engineer", company(), EmploymentType.FULL_TIME,
    WorkplaceType.HYBRID, "Singapore", Salary(5000, 8000, "SGD", "MONTH"), "Description",
    listOf("Requirement"), listOf("Java"), null, Visibility.PUBLIC, JobStatus.ACTIVE,
    "2026-08-11T08:00:00Z", 1, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z", null, null)
private fun company() = Company("company-1", "Real Company", null, null, null, "APPROVED", null, null, null,
    1, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z")
private fun profile() = CandidateProfileDto("candidate-1", "Candidate", "candidate@example.com", "Engineer", null,
    "Singapore", CandidateStats(0, 0, 0, 0), 1, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z")
private fun resume() = Resume("resume-1", "Candidate", 27, "Singapore", "Engineer", "Summary", emptyList(), 1,
    "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z")
private fun application() = CandidateApplication("application-1", "job-1", ApplicationStatus.APPLIED,
    "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z", 1, "Backend Engineer", company(), null, null,
    listOf(TimelineStep(ApplicationStatus.APPLIED, true, "2026-08-11T08:00:00Z")),
    ResumeSnapshot("snapshot-1", "2026-08-11T08:00:00Z", "resume-1", "Candidate", 27, "Singapore",
        "Engineer", "Summary", emptyList(), 1, "2026-08-11T08:00:00Z", "2026-08-11T08:00:00Z"),
    null, listOf(ApplicationNextStep("RECRUITER_REVIEW", "Recruiter review", "Review")))
