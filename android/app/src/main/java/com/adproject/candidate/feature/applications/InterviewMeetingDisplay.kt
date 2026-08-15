package com.adproject.candidate.feature.applications

import com.adproject.candidate.data.contract.Interview
import com.adproject.candidate.data.contract.InterviewMode
import com.adproject.candidate.data.contract.InterviewStatus
import com.adproject.candidate.data.contract.MeetingProvider
import com.adproject.candidate.data.contract.MeetingSyncStatus

/**
 * Candidate-safe presentation decision for the meeting section of an interview
 * card, derived only from fields a candidate is allowed to see. The candidate
 * never receives a token, the Google event id, the internal sync error code, or
 * any raw provider error; those fields simply do not exist in the [Interview]
 * model.
 */
data class MeetingDisplay(
    /** Short provider label ("Google Meet"), or null for a manual interview. */
    val providerLabel: String?,
    /** Neutral status hint for PENDING/FAILED syncs, or null when none applies. */
    val statusHint: String?,
    /** Whether the location is a joinable http(s) link that may be opened. */
    val linkOpenable: Boolean,
)

/**
 * Maps an [Interview] to the single source of truth for how its meeting state is
 * shown. No recruiter-only action ("Reconnect", "Retry sync", "Create Meet") is
 * ever produced here.
 */
fun meetingDisplay(interview: Interview): MeetingDisplay {
    val provider = interview.meetingProvider
    val link = interview.locationOrMeetingUrl
    val isGoogle = provider == MeetingProvider.GOOGLE_MEET

    val providerLabel = if (isGoogle) "Google Meet" else null

    val statusHint: String? = when {
        !isGoogle -> null
        interview.meetingSyncStatus == MeetingSyncStatus.PENDING ->
            "Interview update in progress. Your current invitation remains available."
        interview.meetingSyncStatus == MeetingSyncStatus.FAILED && !link.isNullOrBlank() ->
            "Meeting update could not be completed. Your current invitation is unchanged."
        interview.meetingSyncStatus == MeetingSyncStatus.FAILED ->
            "Meeting invitation is not available yet. Please check back later."
        else -> null
    }

    // A join link is only openable for a scheduled interview with a non-empty
    // ONLINE http(s) link. A cancelled Google Meet has no link, and a completed
    // one is a terminal state with no join action; both are never openable.
    val linkOpenable = !link.isNullOrBlank()
            && interview.mode == InterviewMode.ONLINE
            && (link.startsWith("http://") || link.startsWith("https://"))
            && when (provider) {
                MeetingProvider.GOOGLE_MEET -> interview.status == InterviewStatus.SCHEDULED
                MeetingProvider.MANUAL -> interview.status != InterviewStatus.CANCELLED
            }

    return MeetingDisplay(providerLabel, statusHint, linkOpenable)
}
