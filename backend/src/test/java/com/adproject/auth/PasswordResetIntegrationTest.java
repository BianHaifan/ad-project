package com.adproject.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.PasswordResetMailSender;
import com.adproject.auth.infrastructure.PasswordResetCodeEntity;
import com.adproject.auth.infrastructure.PasswordResetCodeRepository;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PasswordResetIntegrationTest.MailConfiguration.class)
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired FakeMailSender mail;
    @Autowired UserRepository users;
    @Autowired PasswordResetCodeRepository codes;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void resetIsNonEnumerableSingleUseAndRevokesBothAccessAndRefreshSessions() throws Exception {
        String email = "password-reset@example.com";
        JsonNode auth = register(email, "OldPassword1!");
        String access = auth.path("accessToken").asText();
        String refresh = auth.path("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown-password-reset@example.com\"}"))
                .andExpect(status().isAccepted());
        assertThat(mail.lastCode).isNull();

        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", email))))
                .andExpect(status().isAccepted());
        String code = mail.lastCode;
        assertThat(code).matches("\\d{6}");
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                        "email", email, "code", wrongCode, "newPassword", "NewPassword1!"))))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                        "email", email, "code", code, "newPassword", "NewPassword1!"))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/candidate/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                        "email", email, "code", code, "newPassword", "AnotherPassword1!"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", email, "password", "NewPassword1!"))))
                .andExpect(status().isOk());
    }

    @Test
    void expiredAndAttemptLimitedCodesUseTheSameSafeError() throws Exception {
        String expiredEmail = "password-reset-expired@example.com";
        register(expiredEmail, "OldPassword1!");
        String userId = users.findByEmail(expiredEmail).orElseThrow().getId();
        codes.save(new PasswordResetCodeEntity(UUID.randomUUID().toString(), userId,
                passwordEncoder.encode("123456"), Instant.now().minusSeconds(1), Instant.now().plusSeconds(1)));
        confirm(expiredEmail, "123456", "NewPassword1!").andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code")
                        .value("PASSWORD_RESET_INVALID"));

        String limitedEmail = "password-reset-limited@example.com";
        register(limitedEmail, "OldPassword1!");
        request(limitedEmail);
        String validCode = mail.lastCode;
        String wrongCode = validCode.equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            confirm(limitedEmail, wrongCode, "NewPassword1!").andExpect(status().isUnprocessableEntity());
        }
        confirm(limitedEmail, validCode, "NewPassword1!").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void concurrentConfirmationConsumesTheCodeExactlyOnce() throws Exception {
        String email = "password-reset-concurrent@example.com";
        register(email, "OldPassword1!");
        request(email);
        String code = mail.lastCode;
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> confirm(email, code, "NewPassword1!").andReturn().getResponse().getStatus());
            var second = pool.submit(() -> confirm(email, code, "NewPassword1!").andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 422);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void passwordPolicyIsValidatedBeforeConfirmation() throws Exception {
        String email = "password-reset-policy@example.com";
        register(email, "OldPassword1!");
        request(email);
        confirm(email, mail.lastCode, "short").andExpect(status().isUnprocessableEntity());
    }

    private org.springframework.test.web.servlet.ResultActions request(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", email))));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String email, String code, String newPassword)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                        "email", email, "code", code, "newPassword", newPassword))));
    }

    private JsonNode register(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                        "role", "CANDIDATE", "fullName", "Reset Candidate", "email", email,
                        "password", password, "acceptedTermsVersion", "2026-08"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data");
    }

    @TestConfiguration
    static class MailConfiguration {
        @Bean @Primary FakeMailSender fakeMailSender() { return new FakeMailSender(); }
    }

    static class FakeMailSender implements PasswordResetMailSender {
        volatile String lastCode;
        public boolean isConfigured() { return true; }
        public void sendCode(String recipient, String code) { lastCode = code; }
    }
}
