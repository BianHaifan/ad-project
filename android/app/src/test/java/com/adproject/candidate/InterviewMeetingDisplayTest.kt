package com.adproject.candidate

import com.adproject.candidate.data.contract.Interview
import com.adproject.candidate.data.contract.InterviewMode
import com.adproject.candidate.data.contract.InterviewStatus
import com.adproject.candidate.data.contract.MeetingProvider
import com.adproject.candidate.data.contract.MeetingSyncStatus
import com.adproject.candidate.feature.applications.meetingDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewMeetingDisplayTest {

    private fun interview(
        mode: InterviewMode = InterviewMode.ONLINE,
        status: InterviewStatus = InterviewStatus.SCHEDULED,
        link: String? = "https://meet.google.com/abc",
        provider: MeetingProvider = MeetingProvider.MANUAL,
        syncStatus: MeetingSyncStatus = MeetingSyncStatus.NOT_APPLICABLE,
    ) = Interview(
        interviewId = "interview-1",
        applicationId = "application-1",
        scheduledAt = "2026-08-20T09:00:00Z",
        timezone = "Asia/Singapore",
        durationMinutes = 45,
        mode = mode,
        locationOrMeetingUrl = link,
        note = null,
        status = status,
        version = 1,
        createdAt = "2026-08-14T00:00:00Z",
        updatedAt = "2026-08-14T00:00:00Z",
        meetingProvider = provider,
        meetingSyncStatus = syncStatus,
    )

    @Test fun googleMeetReadyShowsLabelAndOpenableLink() {
        val d = meetingDisplay(interview(provider = MeetingProvider.GOOGLE_MEET, syncStatus = MeetingSyncStatus.READY))
        assertEquals("Google Meet", d.providerLabel)
        assertNull(d.statusHint)
        assertTrue(d.linkOpenable)
    }

    @Test fun missingSyncFieldsFallBackToManualWithoutGoogleText() {
        val d = meetingDisplay(interview())
        assertNull(d.providerLabel)
        assertNull(d.statusHint)
        assertTrue(d.linkOpenable)
    }

    @Test fun pendingShowsNeutralHintAndKeepsExistingLink() {
        val d = meetingDisplay(interview(provider = MeetingProvider.GOOGLE_MEET, syncStatus = MeetingSyncStatus.PENDING))
        assertEquals("Google Meet", d.providerLabel)
        assertEquals("Interview update in progress. Your current invitation remains available.", d.statusHint)
        assertTrue(d.linkOpenable)
        // No internal sync error code leaks into the hint.
        assertFalse(d.statusHint!!.contains("SYNC"))
        assertFalse(d.statusHint!!.contains("GOOGLE_MEET_PROVISIONING"))
    }

    @Test fun pendingWithoutLinkShowsHintButNoOpenableLink() {
        val d = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, syncStatus = MeetingSyncStatus.PENDING, link = null),
        )
        assertEquals("Interview update in progress. Your current invitation remains available.", d.statusHint)
        assertFalse(d.linkOpenable)
    }

    @Test fun failedWithLinkKeepsLinkAndNeutralHint() {
        val d = meetingDisplay(interview(provider = MeetingProvider.GOOGLE_MEET, syncStatus = MeetingSyncStatus.FAILED))
        assertEquals("Meeting update could not be completed. Your current invitation is unchanged.", d.statusHint)
        assertTrue(d.linkOpenable)
        assertFalse(d.statusHint!!.contains("GOOGLE_MEET_PROVISIONING"))
    }

    @Test fun failedWithoutLinkShowsUnavailableHint() {
        val d = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, syncStatus = MeetingSyncStatus.FAILED, link = null),
        )
        assertEquals("Meeting invitation is not available yet. Please check back later.", d.statusHint)
        assertFalse(d.linkOpenable)
        assertFalse(d.statusHint!!.contains("GOOGLE_MEET_PROVISIONING"))
    }

    @Test fun cancelledGoogleMeetHasNoOpenableLinkEvenWithLocation() {
        val d = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, syncStatus = MeetingSyncStatus.READY, status = InterviewStatus.CANCELLED),
        )
        assertFalse(d.linkOpenable)
    }

    @Test fun completedGoogleMeetHasNoJoinAction() {
        val d = meetingDisplay(
            interview(provider = MeetingProvider.GOOGLE_MEET, syncStatus = MeetingSyncStatus.READY, status = InterviewStatus.COMPLETED),
        )
        assertFalse(d.linkOpenable)
        assertNull(d.statusHint)
    }

    @Test fun manualInterviewKeepsLegacyLinkBehavior() {
        val scheduled = meetingDisplay(interview())
        assertNull(scheduled.providerLabel)
        assertTrue(scheduled.linkOpenable)

        val cancelled = meetingDisplay(interview(status = InterviewStatus.CANCELLED))
        assertNull(cancelled.providerLabel)
        assertFalse(cancelled.linkOpenable)
    }
}
