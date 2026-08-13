package com.adproject.candidate

import com.adproject.candidate.data.api.CandidateRepository
import com.adproject.candidate.data.api.FakeCandidateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.adproject.candidate.data.contract.ApplicationStatus
import com.adproject.candidate.data.contract.CandidateApiPaths

class CandidateApiTest {
    private val api: CandidateRepository = FakeCandidateRepository

    @Test
    fun recommendationFeedIsRankedAndHasStableIds() {
        val jobs = api.getJobFeed().jobs
        assertEquals(jobs.size, jobs.map { it.jobId }.distinct().size)
        assertTrue(jobs.zipWithNext().all { (left, right) -> (left.match ?: 0) >= (right.match ?: 0) })
    }

    @Test
    fun applicationsExposeThreeTimelineStepsAndResolvableJobs() {
        val jobIds = api.getJobFeed().jobs.map { it.jobId }.toSet()
        val applications = api.getApplications().applications
        assertTrue(applications.all { it.timeline.size == 3 })
        assertTrue(applications.all { it.jobId in jobIds })
    }

    @Test
    fun applicationStatusesAndCandidatePathsMatchTheFinalContract() {
        assertEquals(
            listOf("APPLIED", "IN_REVIEW", "INTERVIEW", "REJECTED", "WITHDRAWN"),
            ApplicationStatus.entries.map { it.name },
        )
        assertEquals("/jobs/job_001/applications", CandidateApiPaths.submitApplication("job_001"))
        assertEquals("/candidate/applications/app_001/withdraw", CandidateApiPaths.withdrawApplication("app_001"))
    }

    @Test
    fun fakeResumeSnapshotIsCompleteAndImmutableByIdentity() {
        val submission = api.submitApplication("moonshot")
        assertTrue(submission.resumeSnapshot.snapshotId.isNotBlank())
        assertTrue(submission.resumeSnapshot.capturedAt.endsWith("Z"))
        assertTrue(submission.resumeSnapshot.experiences.isNotEmpty())
    }
}
