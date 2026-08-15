package com.adproject.integration.google.api;

import java.time.Instant;

public final class GoogleOAuthDtos {
    private GoogleOAuthDtos() {}

    public record AuthorizeResponse(String authorizationUrl) {}

    public record AuthorizationEnvelope(AuthorizeResponse data) {}

    public record ConnectionStatus(boolean connected, String status, Instant connectedAt) {}

    public record ConnectionStatusEnvelope(ConnectionStatus data) {}
}
