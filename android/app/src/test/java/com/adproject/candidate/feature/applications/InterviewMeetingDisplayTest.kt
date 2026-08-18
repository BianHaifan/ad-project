package com.adproject.candidate.feature.applications

import com.adproject.candidate.data.contract.Interview
import com.adproject.candidate.data.contract.InterviewMode
import com.adproject.candidate.data.contract.InterviewStatus
import com.adproject.candidate.data.contract.MeetingProvider
import com.adproject.candidate.data.contract.MeetingSyncStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewMeetingDisplayTest {
    private fun interview(
        mode: InterviewMode = InterviewMode.ONLINE,
        status: InterviewStatus = InterviewStatus.SCHEDULED,
        location: String? = "https://meet.google.com/abc",
        provider: MeetingProvider = MeetingProvider.MANUAL,
        sync: MeetingSyncStatus = MeetingSyncStatus.NOT_APPLICABLE,
    ) = Interview(
        interviewId = "i1", applicationId = "app-1", scheduledAt = "2026-08-20T14:00:00+08:00",
        timezone = "GMT+8", durationMinutes = 45, mode = mode, locationOrMeetingUrl = location,
        note = null, status = status, version = 1, createdAt = "2026-08-10T00:00:00Z",
        updatedAt = "2026-08-10T00:00:00Z", meetingProvider = provider, meetingSyncStatus = sync,
    )

    @Test
    fun manualScheduledOnlineHttpsLinkIsOpenable() {
        val display = meetingDisplay(interview())
        assertNull(display.providerLabel)
        assertNull(display.statusHint)
        assertTrue(display.linkOpenable)
    }

    @Test
    fun googleMeetScheduledGivesLabelAndOpenable() {
        val display = meetingDisplay(interview(provider = MeetingProvider.GOOGLE_MEET))
        assertTrue(display.providerLabel == "Google Meet")
        assertNull(display.statusHint)
        assertTrue(display.linkOpenable)
    }

    @Test
    fun googleMeetPendingGivesHint() {
        val display = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, sync = MeetingSyncStatus.PENDING),
        )
        assertTrue(display.statusHint == "Interview update in progress. Your current invitation remains available.")
        assertTrue(display.linkOpenable)
    }

    @Test
    fun googleMeetFailedWithLinkGivesUnchangedHint() {
        val display = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, sync = MeetingSyncStatus.FAILED),
        )
        assertTrue(display.statusHint == "Meeting update could not be completed. Your current invitation is unchanged.")
    }

    @Test
    fun googleMeetFailedWithoutLinkGivesCheckBackHint() {
        val display = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, sync = MeetingSyncStatus.FAILED, location = null),
        )
        assertTrue(display.statusHint == "Meeting invitation is not available yet. Please check back later.")
        assertFalse(display.linkOpenable)
    }

    @Test
    fun googleMeetCompletedLinkIsNotOpenable() {
        val display = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, status = InterviewStatus.COMPLETED),
        )
        assertFalse(display.linkOpenable)
    }

    @Test
    fun googleMeetCancelledLinkIsNotOpenable() {
        val display = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, status = InterviewStatus.CANCELLED),
        )
        assertNull(display.statusHint)
        assertFalse(display.linkOpenable)
    }

    @Test
    fun manualCancelledLinkIsNotOpenable() {
        val display = meetingDisplay(interview(status = InterviewStatus.CANCELLED))
        assertFalse(display.linkOpenable)
    }

    @Test
    fun nonHttpLinkIsNotOpenable() {
        val display = meetingDisplay(interview(location = "meet.me/abc"))
        assertFalse(display.linkOpenable)
    }

    @Test
    fun onsiteLinkIsNotOpenableEvenWithHttpUrl() {
        val display = meetingDisplay(interview(mode = InterviewMode.ONSITE, location = "https://maps.google.com/x"))
        assertFalse(display.linkOpenable)
    }

    @Test
    fun manualScheduledWithoutLinkIsNotOpenable() {
        val display = meetingDisplay(interview(location = null))
        assertFalse(display.linkOpenable)
    }
}