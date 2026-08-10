package com.adproject.auth.application;

import com.adproject.auth.infrastructure.RefreshTokenEntity;
import com.adproject.auth.infrastructure.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedRefreshToken issue(String userId) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        RefreshTokenEntity entity = new RefreshTokenEntity(UUID.randomUUID().toString(), userId, digest(raw),
                now.plusSeconds(properties.refreshTokenSeconds()), now);
        repository.save(entity);
        return new IssuedRefreshToken(entity, raw);
    }

    public String digest(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(RefreshTokenEntity entity, String rawToken) {}
}
