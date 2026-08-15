package com.adproject.integration.google.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JDK-{@link HttpClient} implementation of the two Calendar calls. Uses a fixed
 * Google Calendar host, an explicit timeout, and never logs the access token or
 * the response body (which may carry the join link or provider error details).
 */
@Component
public class HttpGoogleCalendarClient implements GoogleCalendarClient {
    private static final Logger log = LoggerFactory.getLogger(HttpGoogleCalendarClient.class);
    static final String EVENTS_ENDPOINT = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    @Autowired
    public HttpGoogleCalendarClient(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    /** Package-private constructor for tests to inject a fake transport. */
    HttpGoogleCalendarClient(ObjectMapper mapper, HttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    @Override
    public CalendarEvent createEvent(String accessToken, CalendarEventSpec spec) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(EVENTS_ENDPOINT + "?conferenceDataVersion=1&sendUpdates=all"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(spec)))
                .build();
        HttpResponse<String> response = send(request);
        int status = response.statusCode();
        if (status / 100 == 2) {
            return parse(response.body(), spec.eventId());
        }
        if (status == 401) {
            throw new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED,
                    "Google Calendar rejected the access token");
        }
        if (status == 409) {
            throw new GoogleCalendarException(GoogleCalendarException.Category.CONFLICT,
                    "Google Calendar already has an event with this id");
        }
        log.warn("Google Calendar event insert returned HTTP {}", status);
        throw new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT,
                "Google Calendar event insert failed");
    }

    @Override
    public CalendarEvent getEvent(String accessToken, String eventId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(EVENTS_ENDPOINT + "/" + encode(eventId)))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        int status = response.statusCode();
        if (status / 100 == 2) {
            return parse(response.body(), eventId);
        }
        if (status == 401) {
            throw new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED,
                    "Google Calendar rejected the access token");
        }
        log.warn("Google Calendar event fetch returned HTTP {}", status);
        throw new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT,
                "Google Calendar event fetch failed");
    }

    @Override
    public CalendarEvent patchEvent(String accessToken, String eventId, CalendarEventPatch patch) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(EVENTS_ENDPOINT + "/" + encode(eventId)
                        + "?conferenceDataVersion=1&sendUpdates=all"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patchBody(patch)))
                .build();
        HttpResponse<String> response = send(request);
        int status = response.statusCode();
        if (status / 100 == 2) {
            return parse(response.body(), eventId);
        }
        if (status == 401) {
            throw new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED,
                    "Google Calendar rejected the access token");
        }
        log.warn("Google Calendar event patch returned HTTP {}", status);
        throw new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT,
                "Google Calendar event patch failed");
    }

    @Override
    public void deleteEvent(String accessToken, String eventId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(EVENTS_ENDPOINT + "/" + encode(eventId)
                        + "?sendUpdates=all"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
        HttpResponse<String> response = send(request);
        int status = response.statusCode();
        // A 404 means the event no longer exists remotely, which is already the
        // desired end state for a cancellation.
        if (status / 100 == 2 || status == 404) {
            return;
        }
        if (status == 401) {
            throw new GoogleCalendarException(GoogleCalendarException.Category.UNAUTHORIZED,
                    "Google Calendar rejected the access token");
        }
        log.warn("Google Calendar event delete returned HTTP {}", status);
        throw new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT,
                "Google Calendar event delete failed");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT,
                    "Google Calendar request failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT,
                    "Google Calendar request interrupted");
        }
    }

    private String body(CalendarEventSpec spec) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", spec.eventId());
        root.put("summary", spec.summary());
        root.set("start", timeBlock(spec.startUtc(), spec.timezone()));
        root.set("end", timeBlock(spec.endUtc(), spec.timezone()));
        // A single server-sourced attendee so Google sends the invitation. Never
        // serialized for a PATCH (which must preserve the existing attendees).
        if (spec.attendeeEmail() != null && !spec.attendeeEmail().isBlank()) {
            root.set("attendees", mapper.createArrayNode()
                    .add(mapper.createObjectNode().put("email", spec.attendeeEmail())));
        }
        ObjectNode conferenceData = mapper.createObjectNode();
        ObjectNode createRequest = mapper.createObjectNode();
        createRequest.put("requestId", spec.requestId());
        createRequest.set("conferenceSolutionKey", mapper.createObjectNode().put("type", "hangoutsMeet"));
        conferenceData.set("createRequest", createRequest);
        root.set("conferenceData", conferenceData);
        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize Calendar event", e);
        }
    }

    private String patchBody(CalendarEventPatch patch) {
        ObjectNode root = mapper.createObjectNode();
        root.put("summary", patch.summary());
        root.set("start", timeBlock(patch.startUtc(), patch.timezone()));
        root.set("end", timeBlock(patch.endUtc(), patch.timezone()));
        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize Calendar patch", e);
        }
    }

    private ObjectNode timeBlock(java.time.Instant utc, String timezone) {
        ObjectNode block = mapper.createObjectNode();
        block.put("dateTime", formatLocal(utc, timezone));
        block.put("timeZone", timezone);
        return block;
    }

    private static String formatLocal(java.time.Instant utc, String timezone) {
        return ZonedDateTime.ofInstant(utc, ZoneId.of(timezone)).format(LOCAL_DATE_TIME);
    }

    private CalendarEvent parse(String body, String fallbackEventId) {
        try {
            JsonNode root = mapper.readTree(body);
            String eventId = root.path("id").asText(null);
            if (eventId == null || eventId.isBlank()) {
                eventId = fallbackEventId;
            }
            return new CalendarEvent(eventId, extractHangoutLink(root));
        } catch (IOException e) {
            throw new GoogleCalendarException(GoogleCalendarException.Category.TRANSIENT,
                    "Unable to parse Google Calendar response");
        }
    }

    private String extractHangoutLink(JsonNode root) {
        String hangoutLink = root.path("hangoutLink").asText(null);
        if (hangoutLink != null && !hangoutLink.isBlank()) {
            return hangoutLink;
        }
        JsonNode entryPoints = root.path("conferenceData").path("entryPoints");
        if (entryPoints.isArray()) {
            for (JsonNode entry : entryPoints) {
                if ("video".equals(entry.path("entryPointType").asText())) {
                    String uri = entry.path("uri").asText(null);
                    if (uri != null && !uri.isBlank()) {
                        return uri;
                    }
                }
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
