package com.adproject.integration.google;

/**
 * Port for provisioning online meetings through an external provider.
 *
 * <p>No provider SDK types cross this boundary. The interview service depends on
 * this port rather than on Google specifics, so the Google integration can evolve
 * behind it without leaking into application DTOs or {@code InterviewService}.
 *
 * <p>{@link #provision} performs the external HTTP work and returns a
 * provider-neutral result; it never throws on a remote failure, so a transient
 * Google error becomes a persisted {@link ProvisionOutcome#PENDING} or
 * {@link ProvisionOutcome#FAILED} state instead of a rolled-back interview.
 */
public interface MeetingProvisioningPort {

    /**
     * Returns {@code true} when the recruiter has a usable (connected) provider
     * connection.
     */
    boolean isConnected(String recruiterId);

    /**
     * Returns {@code true} when the system can actually create a meeting right now.
     */
    boolean isProvisioningAvailable(String recruiterId);

    /**
     * Returns {@code true} when the recruiter previously connected but the
     * connection has since become unusable (the refresh token is revoked or
     * invalid), so they must reconnect rather than receive a generic error.
     */
    boolean requiresReconnect(String recruiterId);

    /**
     * Confirms, before any local interview or application state is created, that
     * the recruiter's connection is usable for provisioning. This must be called
     * outside any business transaction or application lock.
     *
     * <p>For a still-valid access token it makes no external call. For a token
     * nearing expiry it refreshes once and persists the new tokens; an
     * {@code invalid_grant} marks the connection revoked, and a transient refresh
     * failure is reported as unavailable. In every failure case no local
     * interview may have been created yet.
     *
     * @throws MeetingProvisioningException with a safe {@code GOOGLE_MEET_*} code
     *         ({@code NOT_CONNECTED}, {@code RECONNECT_REQUIRED}, or
     *         {@code PROVISIONING_UNAVAILABLE}).
     */
    void ensureConnectionUsable(String recruiterId);

    /**
     * Creates (or recovers) the external meeting for an interview. The caller is
     * expected to have already committed the local interview in {@code PENDING}
     * state; the returned result is then written back to that interview in a
     * separate short transaction.
     */
    ProvisionResult provision(ProvisionRequest request);

    /**
     * Updates the time/timezone/duration/summary of an existing meeting. The
     * event id is the already-persisted external id; the caller passes it back so
     * no second meeting is ever created. Never touches conference data, so the
     * existing link stays valid.
     *
     * @return {@link MeetingSyncResult} describing SYNCED or FAILED with a safe code.
     */
    MeetingSyncResult updateMeeting(MeetingUpdateRequest request);

    /**
     * Cancels (deletes) an existing meeting. A remote 404 — the event is already
     * gone — counts as a successful cancellation. Never throws on a remote
     * failure; failures carry a safe code so the local interview can stay
     * SCHEDULED with its original time and link.
     *
     * @return {@link MeetingSyncResult} describing SYNCED or FAILED with a safe code.
     */
    MeetingSyncResult cancelMeeting(MeetingCancelRequest request);
}
