package com.adproject.integration.google.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Focuses on the HTTP-level mapping that the service layer cannot observe with a
 * mocked {@link GoogleCalendarClient}: PATCH query parameters, the absence of
 * conference data in a patch, and DELETE 404-as-success.
 */
class HttpGoogleCalendarClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void deleteEventTreats404AsSuccess() throws Exception {
        HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(mapper, clientReturning(404, "{}"));

        client.deleteEvent("token", "evt-1"); // must not throw
    }

    @Test void deleteEventThrowsTransientOnServerError() throws Exception {
        HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(mapper, clientReturning(500, "{}"));

        assertThatThrownBy(() -> client.deleteEvent("token", "evt-1"))
                .isInstanceOf(GoogleCalendarException.class)
                .satisfies(e -> assertThat(((GoogleCalendarException) e).category())
                        .isEqualTo(GoogleCalendarException.Category.TRANSIENT));
    }

    @Test void deleteEventThrowsUnauthorizedOn401() throws Exception {
        HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(mapper, clientReturning(401, "{}"));

        assertThatThrownBy(() -> client.deleteEvent("token", "evt-1"))
                .isInstanceOf(GoogleCalendarException.class)
                .satisfies(e -> assertThat(((GoogleCalendarException) e).category())
                        .isEqualTo(GoogleCalendarException.Category.UNAUTHORIZED));
    }

    @Test void patchEventUsesSyncQueryAndNeverSendsConferenceDataOrAttendees() throws Exception {
        HttpClient httpClient = clientReturning(200, "{\"id\":\"evt-1\"}");
        HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(mapper, httpClient);

        client.patchEvent("token", "evt-1", new CalendarEventPatch("Recruitment interview",
                Instant.parse("2026-08-20T09:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"), "Asia/Singapore"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("PATCH");
        assertThat(request.uri().toString()).contains("conferenceDataVersion=1").contains("sendUpdates=all");
        String body = bodyOf(request);
        assertThat(body).contains("\"summary\":\"Recruitment interview\"")
                .contains("\"start\"").contains("\"end\"")
                .doesNotContain("conferenceData").doesNotContain("createRequest")
                .doesNotContain("attendees");
    }

    @Test void createEventSerializesSingleAttendeeAndSendsNotifications() throws Exception {
        HttpClient httpClient = clientReturning(200, "{\"id\":\"evt-1\",\"hangoutLink\":\"https://meet.google.com/abc\"}");
        HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(mapper, httpClient);

        client.createEvent("token", new CalendarEventSpec("evt-1", "Recruitment interview",
                Instant.parse("2026-08-20T09:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"),
                "Asia/Singapore", "req-1", "candidate@example.com"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).contains("conferenceDataVersion=1").contains("sendUpdates=all");
        String body = bodyOf(request);
        assertThat(body).contains("\"attendees\"").contains("\"candidate@example.com\"")
                .contains("\"conferenceData\"").contains("\"createRequest\"");
    }

    @Test void createEventOmitsAttendeeWhenEmailBlank() throws Exception {
        HttpClient httpClient = clientReturning(200, "{\"id\":\"evt-1\"}");
        HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(mapper, httpClient);

        client.createEvent("token", new CalendarEventSpec("evt-1", "Recruitment interview",
                Instant.parse("2026-08-20T09:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"),
                "Asia/Singapore", "req-1", "   "));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(bodyOf(captor.getValue())).doesNotContain("attendees");
    }

    @Test void deleteEventSendsNotificationsToAll() throws Exception {
        HttpClient httpClient = clientReturning(404, "{}");
        HttpGoogleCalendarClient client = new HttpGoogleCalendarClient(mapper, httpClient);

        client.deleteEvent("token", "evt-1");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertThat(captor.getValue().uri().toString()).contains("sendUpdates=all");
    }

    private static HttpClient clientReturning(int status, String body) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(response);
        return httpClient;
    }

    private static String bodyOf(HttpRequest request) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                buffer.writeBytes(bytes);
            }
            @Override public void onError(Throwable throwable) { throw new RuntimeException(throwable); }
            @Override public void onComplete() {}
        });
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
