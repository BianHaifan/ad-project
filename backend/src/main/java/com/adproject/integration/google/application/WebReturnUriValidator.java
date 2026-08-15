package com.adproject.integration.google.application;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Validates the server-configured Google OAuth "web return URI": the single
 * fixed address the OAuth callback redirects the recruiter's browser to after
 * authorization completes. The value is read only from {@code
 * GOOGLE_OAUTH_WEB_RETURN_URI} and never from a client.
 *
 * <p>Rules: the URI must be absolute, carry no userinfo or fragment, and use
 * HTTPS. Plain HTTP is allowed only for an explicit loopback host ({@code
 * localhost} or an IP loopback address during local development.
 */
public final class WebReturnUriValidator {
    private WebReturnUriValidator() {}

    /**
     * Parses and validates a web return URI.
     *
     * @return the validated {@link URI}, or {@code null} when {@code raw} is blank
     * @throws IllegalArgumentException when {@code raw} is present but invalid
     */
    public static URI parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Web return URI is not a valid URI", e);
        }
        validate(uri);
        return uri;
    }

    private static void validate(URI uri) {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("Web return URI must be absolute");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Web return URI must not contain userinfo");
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("Web return URI must not contain a fragment");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Web return URI must have a host");
        }
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            if (isLoopback(host)) {
                return;
            }
            throw new IllegalArgumentException("Plain HTTP web return URI is allowed only for a loopback host");
        }
        throw new IllegalArgumentException("Web return URI must use https, or loopback http for local development");
    }

    private static boolean isLoopback(String host) {
        String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        if ("localhost".equalsIgnoreCase(normalized)) {
            return true;
        }
        if (!isIpLiteral(normalized)) {
            return false;
        }
        try {
            return InetAddress.getByName(normalized).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isIpLiteral(String host) {
        return !host.isBlank() && host.chars().allMatch(character ->
                Character.digit(character, 16) >= 0 || character == ':' || character == '.');
    }
}
