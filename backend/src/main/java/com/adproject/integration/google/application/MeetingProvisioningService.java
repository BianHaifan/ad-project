package com.adproject.integration.google.application;

import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.integration.google.MeetingCancelRequest;
import com.adproject.integration.google.MeetingProvisioningException;
import com.adproject.integration.google.MeetingProvisioningPort;
import com.adproject.integration.google.MeetingSyncOutcome;
import com.adproject.integration.google.MeetingSyncResult;
import com.adproject.integration.google.MeetingUpdateRequest;
import com.adproject.integration.google.ProvisionOutcome;
import com.adproject.integration.google.ProvisionRequest;
import com.adproject.integration.google.ProvisionResult;
import com.adproject.integration.google.domain.GoogleConnectionStatus;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionEntity;
import com.adproject.integration.google.infrastructure.GoogleRecruiterConnectionRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Real Google Calendar / Meet provisioning. Creates (or recovers) an event with
 * Meet conference data on the recruiter's {@code primary} calendar and returns a
 * provider-neutral {@link ProvisionResult}. All Google HTTP work happens here,
 * outside any caller's business transaction, and every remote failure is
 * translated into a safe error code rather than surfacing a provider response
 * body or leaking tokens.
 */
@Component
public class MeetingProvisioningService implements MeetingProvisioningPort {
    static final Duration REFRESH_BUFFER = Duration.ofMinutes(5);
    static final String EVENT_SUMMARY = "Recruitment interview";
    static final int MAX_POLLS = 3;
    static final long POLL_INTERVAL_MILLIS = 500;
    static final String CODE_RECONNECT = "GOOGLE_MEET_RECONNECT_REQUIRED";
    static final String CODE_UNAVAILABLE = "GOOGLE_MEET_PROVISIONING_UNAVAILABLE";
    static final String CODE_NOT_CONNECTED = "GOOGLE_MEET_NOT_CONNECTED";
    static final String CODE_LINK_INVALID = "GOOGLE_MEET_LINK_INVALID";

    private final GoogleRecruiterConnectionRepository connections;
    private final SecretCipher cipher;
    private final GoogleCalendarClient calendarClient;
    private final GoogleTokenClient tokenClient;
    private final GoogleConnectionStore connectionStore;
    private final GoogleOAuthProperties properties;
    private final Clock clock;

    public MeetingProvisioningService(GoogleRecruiterConnectionRepository connections, SecretCipher cipher,
                                      GoogleCalendarClient calendarClient, GoogleTokenClient tokenClient,
                                      GoogleConnectionStore connectionStore, GoogleOAuthProperties properties,
                                      Clock clock) {
        this.connections = connections;
        this.cipher = cipher;
        this.calendarClient = calendarClient;
        this.tokenClient = tokenClient;
        this.connectionStore = connectionStore;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean isConnected(String recruiterId) {
        return connections.existsByRecruiterIdAndStatus(recruiterId, GoogleConnectionStatus.CONNECTED);
    }

    @Override
    public boolean requiresReconnect(String recruiterId) {
        return connections.existsByRecruiterIdAndStatus(recruiterId, GoogleConnectionStatus.REVOKED);
    }

    @Override
    public boolean isProvisioningAvailable(String recruiterId) {
        return isConnected(recruiterId) && properties.isConfigured();
    }

    @Override
    public void ensureConnectionUsable(String recruiterId) {
        GoogleRecruiterConnectionEntity connection = connections.findByRecruiterId(recruiterId).orElse(null);
        if (connection == null) {
            throw new MeetingProvisioningException(CODE_NOT_CONNECTED);
        }
        // A revoked connection must surface as "reconnect", never as a generic
        // "not connected" or a later failed interview.
        if (connection.getStatus() == GoogleConnectionStatus.REVOKED) {
            throw new MeetingProvisioningException(CODE_RECONNECT);
        }
        if (connection.getStatus() != GoogleConnectionStatus.CONNECTED) {
            throw new MeetingProvisioningException(CODE_NOT_CONNECTED);
        }
        if (!properties.isConfigured()) {
            throw new MeetingProvisioningException(CODE_UNAVAILABLE);
        }
        Tokens tokens = new Tokens(cipher.decrypt(connection.getAccessTokenEncrypted()),
                cipher.decrypt(connection.getRefreshTokenEncrypted()), connection.getAccessTokenExpiresAt());
        // Only refresh when the access token is about to expire; a still-valid
        // token triggers no external call. Any refresh failure happens here, before
        // the local interview or application state is created.
        if (tokens.expiresAt().isBefore(clock.instant().plus(REFRESH_BUFFER))) {
            refreshTokens(tokens, recruiterId);
        }
    }

    @Override
    public ProvisionResult provision(ProvisionRequest request) {
        GoogleRecruiterConnectionEntity connection = connections.findByRecruiterId(request.recruiterId()).orElse(null);
        if (connection == null || connection.getStatus() != GoogleConnectionStatus.CONNECTED) {
            return fail(null, CODE_NOT_CONNECTED);
        }
        Tokens tokens = new Tokens(cipher.decrypt(connection.getAccessTokenEncrypted()),
                cipher.decrypt(connection.getRefreshTokenEncrypted()), connection.getAccessTokenExpiresAt());
        Instant now = clock.instant();

        // Refresh near expiry before the Calendar call so a barely-expired token
        // does not force a 401 round-trip.
        if (tokens.expiresAt().isBefore(now.plus(REFRESH_BUFFER))) {
            try {
                tokens = refreshTokens(tokens, request.recruiterId());
            } catch (MeetingProvisioningException f) {
                return fail(null, f.code());
            }
        }

        String eventId = GoogleCalendarEventId.fromCorrelationId(request.correlationId()).value();
        CalendarEventSpec spec = new CalendarEventSpec(eventId, EVENT_SUMMARY,
                request.scheduledAt(), request.scheduledAt().plusSeconds(request.durationMinutes() * 60L),
                request.timezone(), request.correlationId(), request.attendeeEmail());

        EventCreation creation;
        try {
            creation = createEvent(tokens, spec, request.recruiterId());
        } catch (MeetingProvisioningException f) {
            return fail(null, f.code());
        }
        return settle(creation.event(), creation.tokens(), spec, request.recruiterId());
    }

    @Override
    public MeetingSyncResult updateMeeting(MeetingUpdateRequest request) {
        return mutate(request.recruiterId(), accessToken -> {
            CalendarEventPatch patch = new CalendarEventPatch(EVENT_SUMMARY, request.scheduledAt(),
                    request.scheduledAt().plusSeconds(request.durationMinutes() * 60L), request.timezone());
            calendarClient.patchEvent(accessToken, request.eventId(), patch);
        });
    }

    @Override
    public MeetingSyncResult cancelMeeting(MeetingCancelRequest request) {
        return mutate(request.recruiterId(), accessToken ->
                calendarClient.deleteEvent(accessToken, request.eventId()));
    }

    /**
     * Runs an update or cancel mutation against the provider, translating every
     * remote failure into a safe, provider-neutral {@link MeetingSyncResult}. The
     * caller has already reserved the local interview as {@code PENDING} and
     * supplied the known external event id, so no second meeting is ever created.
     */
    private MeetingSyncResult mutate(String recruiterId, java.util.function.Consumer<String> calendarCall) {
        GoogleRecruiterConnectionEntity connection = connections.findByRecruiterId(recruiterId).orElse(null);
        if (connection == null || connection.getStatus() != GoogleConnectionStatus.CONNECTED) {
            String code = connection != null && connection.getStatus() == GoogleConnectionStatus.REVOKED
                    ? CODE_RECONNECT : CODE_NOT_CONNECTED;
            return new MeetingSyncResult(MeetingSyncOutcome.FAILED, code);
        }
        Tokens tokens = new Tokens(cipher.decrypt(connection.getAccessTokenEncrypted()),
                cipher.decrypt(connection.getRefreshTokenEncrypted()), connection.getAccessTokenExpiresAt());
        if (tokens.expiresAt().isBefore(clock.instant().plus(REFRESH_BUFFER))) {
            try {
                tokens = refreshTokens(tokens, recruiterId);
            } catch (MeetingProvisioningException f) {
                return new MeetingSyncResult(MeetingSyncOutcome.FAILED, f.code());
            }
        }
        try {
            withAuthRetry(tokens, recruiterId, accessToken -> {
                calendarCall.accept(accessToken);
                return Boolean.TRUE;
            });
            return new MeetingSyncResult(MeetingSyncOutcome.SYNCED, null);
        } catch (MeetingProvisioningException f) {
            return new MeetingSyncResult(MeetingSyncOutcome.FAILED, f.code());
        }
    }

    /**
     * Creates the event, translating 409 into a GET recovery. A single 401 on
     * either the insert or the recovery GET is handled by {@link #withAuthRetry}.
     * Never throws a provider-specific exception out of the package; it throws
     * {@link MeetingProvisioningException} carrying a safe code instead.
     */
    private EventCreation createEvent(Tokens tokens, CalendarEventSpec spec, String recruiterId) {
        try {
            AuthedCall<CalendarEvent> created = withAuthRetry(tokens, recruiterId,
                    accessToken -> calendarClient.createEvent(accessToken, spec));
            return new EventCreation(created.value(), created.tokens());
        } catch (GoogleCalendarException conflict) {
            // Only CONFLICT escapes withAuthRetry; recover the existing event by id.
            AuthedCall<CalendarEvent> recovered = recover(tokens, spec, recruiterId);
            return new EventCreation(recovered.value(), recovered.tokens());
        }
    }

    private AuthedCall<CalendarEvent> recover(Tokens tokens, CalendarEventSpec spec, String recruiterId) {
        return withAuthRetry(tokens, recruiterId,
                accessToken -> calendarClient.getEvent(accessToken, spec.eventId()));
    }

    private ProvisionResult settle(CalendarEvent event, Tokens tokens, CalendarEventSpec spec, String recruiterId) {
        String link = event.hangoutLink();
        if (link != null) {
            return linkResult(event, link);
        }
        // The conference is provisioned asynchronously; poll a few times and only
        // accept a server-verified HTTPS meet link. If none appears, stay PENDING
        // rather than fabricating a URL.
        CalendarEvent current = event;
        Tokens effective = tokens;
        for (int i = 0; i < MAX_POLLS; i++) {
            pause();
            try {
                AuthedCall<CalendarEvent> fetched = withAuthRetry(effective, recruiterId,
                        accessToken -> calendarClient.getEvent(accessToken, spec.eventId()));
                current = fetched.value();
                effective = fetched.tokens();
            } catch (MeetingProvisioningException f) {
                return new ProvisionResult(ProvisionOutcome.FAILED, event.eventId(), null, f.code());
            } catch (GoogleCalendarException e) {
                return new ProvisionResult(ProvisionOutcome.FAILED, event.eventId(), null, CODE_UNAVAILABLE);
            }
            link = current.hangoutLink();
            if (link != null) {
                return linkResult(current, link);
            }
        }
        return new ProvisionResult(ProvisionOutcome.PENDING, current.eventId(), null, null);
    }

    /**
     * Runs one Calendar call, and on a single 401 refreshes the token and retries
     * exactly once. It never loops: a second failure surfaces as unavailable
     * (unless it is a {@code CONFLICT}, which the insert caller recovers from via
     * GET). CONFLICT is re-thrown for the caller; other transient failures map to
     * a safe unavailable code, and an invalid grant maps to reconnect.
     */
    private <T> AuthedCall<T> withAuthRetry(Tokens tokens, String recruiterId, Function<String, T> call) {
        try {
            return new AuthedCall<>(call.apply(tokens.accessToken()), tokens);
        } catch (GoogleCalendarException e) {
            if (e.category() == GoogleCalendarException.Category.CONFLICT) {
                throw e;
            }
            if (e.category() != GoogleCalendarException.Category.UNAUTHORIZED) {
                throw new MeetingProvisioningException(CODE_UNAVAILABLE);
            }
            Tokens refreshed = refreshTokens(tokens, recruiterId);
            try {
                return new AuthedCall<>(call.apply(refreshed.accessToken()), refreshed);
            } catch (GoogleCalendarException retry) {
                if (retry.category() == GoogleCalendarException.Category.CONFLICT) {
                    throw retry;
                }
                throw new MeetingProvisioningException(CODE_UNAVAILABLE);
            }
        }
    }

    private Tokens refreshTokens(Tokens tokens, String recruiterId) {
        RefreshedToken refreshed;
        try {
            refreshed = tokenClient.refreshAccessToken(tokens.refreshToken());
        } catch (GoogleTokenRefreshException e) {
            if (e.category() == GoogleTokenRefreshException.Category.INVALID_GRANT) {
                connectionStore.markRevoked(recruiterId, clock.instant());
                throw new MeetingProvisioningException(CODE_RECONNECT);
            }
            throw new MeetingProvisioningException(CODE_UNAVAILABLE);
        }
        // Google does not always rotate the refresh token; keep the old one then.
        String newRefresh = refreshed.refreshToken() != null ? refreshed.refreshToken() : tokens.refreshToken();
        Instant now = clock.instant();
        Tokens result = new Tokens(refreshed.accessToken(), newRefresh,
                DatabaseTimePrecision.micros(now.plusSeconds(refreshed.expiresInSeconds())));
        connectionStore.updateTokens(recruiterId, cipher.encrypt(result.accessToken()),
                cipher.encrypt(result.refreshToken()), result.expiresAt(), now);
        return result;
    }

    private static ProvisionResult linkResult(CalendarEvent event, String link) {
        return isValidMeetLink(link)
                ? new ProvisionResult(ProvisionOutcome.READY, event.eventId(), link, null)
                : new ProvisionResult(ProvisionOutcome.FAILED, event.eventId(), null, CODE_LINK_INVALID);
    }

    private static boolean isValidMeetLink(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(link);
            return "https".equalsIgnoreCase(uri.getScheme()) && "meet.google.com".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ProvisionResult fail(String eventId, String code) {
        return new ProvisionResult(ProvisionOutcome.FAILED, eventId, null, code);
    }

    private record Tokens(String accessToken, String refreshToken, Instant expiresAt) {}

    private record EventCreation(CalendarEvent event, Tokens tokens) {}

    private record AuthedCall<T>(T value, Tokens tokens) {}
}
