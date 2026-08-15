package com.adproject.integration.google.application;

/**
 * Thrown when the Google token endpoint rejects a request or returns an
 * unusable response. The message deliberately carries only a status class or a
 * generic cause, never a token, secret, or the raw response body.
 */
public class GoogleOAuthTokenExchangeException extends RuntimeException {

    public GoogleOAuthTokenExchangeException(String message) {
        super(message);
    }

    public GoogleOAuthTokenExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
