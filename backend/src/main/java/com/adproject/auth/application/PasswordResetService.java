package com.adproject.auth.application;

import com.adproject.auth.api.PasswordResetConfirmRequest;
import com.adproject.auth.infrastructure.PasswordResetCodeEntity;
import com.adproject.auth.infrastructure.PasswordResetCodeRepository;
import com.adproject.auth.infrastructure.RefreshTokenRepository;
import com.adproject.common.api.ApiException;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final Duration EXPIRES_AFTER = Duration.ofMinutes(15);
    private static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final MailSender mailSender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetCodeRepository codes,
                                RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                                MailSender mailSender, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.clock = clock;
    }

    @Transactional
    public void request(String rawEmail) {
        requireConfigured();
        String email = normalize(rawEmail);
        UserEntity user = users.findByEmail(email).orElse(null);
        if (user == null) return;
        Instant now = clock.instant();
        var latest = codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (latest != null && latest.getCreatedAt().plus(RESEND_AFTER).isAfter(now)) return;
        String code = "%06d".formatted(random.nextInt(1_000_000));
        codes.save(new PasswordResetCodeEntity(UUID.randomUUID().toString(), user.getId(),
                passwordEncoder.encode(code), now.plus(EXPIRES_AFTER), now));
        mailSender.send(email, "Your HireX password reset code",
                "Your HireX verification code is " + code + ". It expires in 15 minutes.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void confirm(PasswordResetConfirmRequest request) {
        Instant now = clock.instant();
        UserEntity user = users.findByEmail(normalize(request.email())).orElseThrow(PasswordResetService::invalid);
        PasswordResetCodeEntity code = codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(PasswordResetService::invalid);
        if (code.getConsumedAt() != null || !code.getExpiresAt().isAfter(now)
                || code.getAttemptCount() >= MAX_ATTEMPTS) throw invalid();
        if (!passwordEncoder.matches(request.code(), code.getCodeHash())) {
            code.recordFailedAttempt();
            throw invalid();
        }
        UserEntity lockedUser = users.findByIdForUpdate(user.getId()).orElseThrow(PasswordResetService::invalid);
        lockedUser.resetPassword(passwordEncoder.encode(request.newPassword()), now);
        code.consume(now);
        refreshTokens.revokeAllActiveForUser(user.getId(), now);
    }

    private void requireConfigured() {
        if (!mailSender.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PASSWORD_RESET_EMAIL_NOT_CONFIGURED",
                    "Password reset email delivery is not configured");
        }
    }

    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_RESET_INVALID",
                "The email, code, or reset request is invalid");
    }
}
