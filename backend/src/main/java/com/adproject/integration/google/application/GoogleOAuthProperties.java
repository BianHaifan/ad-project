package com.adproject.integration.google.application;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local-only Google OAuth configuration. Every value is optional so the
 * application can start and fail closed (rather than fail at boot) when the
 * project owner has not yet supplied credentials.
 *
 * <p>None of these values is ever accepted from a client; the authorization
 * URL uses a fixed Google host, a fixed minimal Calendar scope, and the
 * server-configured redirect URI only. The web return URI is likewise a fixed,
 * server-only value used to hand the browser back to the recruiter web app.
 */
@ConfigurationProperties(prefix = "app.google-oauth")
public record GoogleOAuthProperties(String clientId, String clientSecret, String redirectUri,
                                    String tokenEncryptionKey, String webReturnUri) {

    /**
     * True when every value required to run the OAuth flow is present and valid.
     * The encryption key must decode to a 256-bit (32-byte) AES key, and the web
     * return URI must pass {@link #resolvedWebReturnUri()} validation.
     */
    public boolean isConfigured() {
        return isNotBlank(clientId) && isNotBlank(clientSecret) && isNotBlank(redirectUri)
                && SecretCipher.resolveKey(tokenEncryptionKey) != null
                && resolvedWebReturnUri() != null;
    }

    /**
     * The validated browser return URI for the OAuth callback, or {@code null}
     * when it is not configured. Throws {@link IllegalArgumentException} when the
     * configured value is present but invalid, so a misconfigured value fails the
     * callback closed and is rejected at startup.
     */
    public URI resolvedWebReturnUri() {
        return WebReturnUriValidator.parse(webReturnUri);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
