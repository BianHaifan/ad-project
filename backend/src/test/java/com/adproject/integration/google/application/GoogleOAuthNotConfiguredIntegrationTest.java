package com.adproject.integration.google.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.google-oauth.client-id=",
        "app.google-oauth.client-secret=",
        "app.google-oauth.redirect-uri=",
        "app.google-oauth.token-encryption-key=",
})
@AutoConfigureMockMvc @ActiveProfiles("test")
class GoogleOAuthNotConfiguredIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;

    @Test void authorizeFailsClosedWhenNotConfigured() throws Exception {
        String id = UUID.randomUUID().toString();
        UserEntity recruiter = users.save(new UserEntity(id, id + "@example.com", "hash", "Recruiter",
                UserRole.RECRUITER, UserStatus.ACTIVE, "2026-08", Instant.now(), Instant.now()));

        mvc.perform(post("/api/v1/recruiter/google-oauth/authorize")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(recruiter)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_OAUTH_NOT_CONFIGURED"));
    }

    @Test void callbackFailsClosedWithoutRedirectWhenNotConfigured() throws Exception {
        mvc.perform(get("/api/v1/recruiter/google-oauth/callback").param("code", "fake").param("state", "whatever"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("GOOGLE_OAUTH_NOT_CONFIGURED"))
                .andExpect(header().doesNotExist("Location"));
    }
}
