package com.adproject.integration.google.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.integration.google.application.GoogleOAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GoogleOAuthController {
    private final GoogleOAuthService service;
    private final GoogleOAuthCallbackPresenter callbackPresenter;

    public GoogleOAuthController(GoogleOAuthService service, GoogleOAuthCallbackPresenter callbackPresenter) {
        this.service = service;
        this.callbackPresenter = callbackPresenter;
    }

    @PostMapping("/api/v1/recruiter/google-oauth/authorize")
    GoogleOAuthDtos.AuthorizationEnvelope begin(@AuthenticationPrincipal AuthenticatedUser user) {
        return new GoogleOAuthDtos.AuthorizationEnvelope(service.beginAuthorization(user));
    }

    @GetMapping("/api/v1/recruiter/google-oauth/status")
    GoogleOAuthDtos.ConnectionStatusEnvelope status(@AuthenticationPrincipal AuthenticatedUser user) {
        return new GoogleOAuthDtos.ConnectionStatusEnvelope(service.status(user));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/v1/recruiter/google-oauth")
    void disconnect(@AuthenticationPrincipal AuthenticatedUser user) {
        service.disconnect(user);
    }

    @GetMapping("/api/v1/recruiter/google-oauth/callback")
    ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {
        return callbackPresenter.redirect(service.handleCallback(code, state, error));
    }
}
