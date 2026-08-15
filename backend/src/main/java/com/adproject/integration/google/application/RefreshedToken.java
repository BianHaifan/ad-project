package com.adproject.integration.google.application;

/**
 * Result of a refresh-token grant. {@code refreshToken} is null when Google did
 * not rotate it, in which case the caller keeps the previously stored value.
 */
public record RefreshedToken(String accessToken, long expiresInSeconds, String refreshToken) {}
