package com.adproject.integration.google.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Random-token, PKCE, and state-hash helpers for the OAuth authorization flow.
 * All values are URL-safe (Base64url, unpadded) where they travel in a URL, and
 * the stored state identifier is the SHA-256 hex digest of the raw state.
 */
final class OAuthUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private OAuthUtil() {}

    static String generateState() {
        return randomToken();
    }

    static String generateCodeVerifier() {
        return randomToken();
    }

    static String codeChallenge(String codeVerifier) {
        return URL_ENCODER.encodeToString(sha256(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
    }

    static String sha256Hex(String value) {
        byte[] digest = sha256(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
