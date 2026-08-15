package com.adproject.integration.google.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts short secrets (OAuth tokens, PKCE verifiers) at rest
 * using JDK AES-GCM. The key comes only from {@code GOOGLE_TOKEN_ENCRYPTION_KEY}
 * (a Base64-encoded 256-bit key); when it is absent the cipher fails closed
 * rather than ever writing plaintext.
 *
 * <p>Ciphertext layout: {@code Base64(12-byte IV || ciphertext || 16-byte tag)}.
 */
@Component
public class SecretCipher {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public SecretCipher(GoogleOAuthProperties properties) {
        this.key = resolveKey(properties.tokenEncryptionKey());
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            int combinedLength = Math.addExact(IV_LENGTH_BYTES, ciphertext.length);
            byte[] combined = new byte[combinedLength];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException | ArithmeticException e) {
            throw new IllegalStateException("Unable to encrypt secret", e);
        }
    }

    public String decrypt(String encrypted) {
        requireKey();
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("Ciphertext is too short");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt secret", e);
        }
    }

    /**
     * Resolves a Base64-encoded 256-bit key, or {@code null} when the value is
     * absent or not a valid key. Never throws.
     */
    static SecretKey resolveKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(base64Key);
            if (raw.length != 32) {
                return null;
            }
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("Token encryption key is not configured");
        }
    }
}
