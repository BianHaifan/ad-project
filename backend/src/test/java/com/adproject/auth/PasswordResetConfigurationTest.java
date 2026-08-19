package com.adproject.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.adproject.auth.application.MailSender;
import com.adproject.auth.application.PasswordResetService;
import com.adproject.auth.infrastructure.PasswordResetCodeRepository;
import com.adproject.auth.infrastructure.RefreshTokenRepository;
import com.adproject.common.api.ApiException;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetConfigurationTest {
    @Test
    void unconfiguredDeliveryFailsBeforeAccountLookup() {
        MailSender disabled = new MailSender() {
            public boolean isConfigured() { return false; }
            public void send(String recipient, String subject, String text) { throw new AssertionError("must not send"); }
        };
        var service = new PasswordResetService(mock(UserRepository.class), mock(PasswordResetCodeRepository.class),
                mock(RefreshTokenRepository.class), mock(PasswordEncoder.class), disabled, Clock.systemUTC());

        assertThatThrownBy(() -> service.request("unknown@example.com"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("PASSWORD_RESET_EMAIL_NOT_CONFIGURED");
    }
}
