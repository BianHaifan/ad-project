package com.adproject.integration.google.application;

public record TokenExchangeResult(String accessToken, String refreshToken, long expiresInSeconds,
                                  String tokenType) {}
