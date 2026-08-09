package com.adproject.candidate

import com.adproject.candidate.data.api.CandidateApi
import com.adproject.candidate.data.api.FakeCandidateApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateApiTest {
    private val api: CandidateApi = FakeCandidateApi

    @Test
    fun recommendationFeedIsRankedAndHasStableIds() {
        val jobs = api.getJobFeed().jobs
        assertEquals(jobs.size, jobs.map { it.id }.distinct().size)
        assertTrue(jobs.zipWithNext().all { (left, right) -> left.match >= right.match })
    }

    @Test
    fun applicationsExposeThreeTimelineStepsAndResolvableJobs() {
        val jobIds = api.getJobFeed().jobs.map { it.id }.toSet()
        val applications = api.getApplications().applications
        assertTrue(applications.all { it.steps.size == 3 })
        assertTrue(applications.all { it.jobId in jobIds })
    }

    @Test
    fun sentChatMessageIsReturnedAsOutgoing() {
        val message = api.sendMessage("mia", "Hello")
        assertEquals("Hello", message.body)
        assertTrue(message.sent)
    }
}
