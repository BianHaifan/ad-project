package com.adproject.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void candidateRegistrationReturnsContractAndStoresOnlyPasswordHash() throws Exception {
        String email = uniqueEmail("candidate");
        Integer companiesBefore = jdbcTemplate.queryForObject("select count(*) from companies", Integer.class);
        JsonNode response = registerCandidate(email, "StrongPass123!");

        assertThat(response.at("/data/user/role").asText()).isEqualTo("CANDIDATE");
        assertThat(response.at("/data/user/company").isNull()).isTrue();
        assertThat(response.at("/data/user/createdAt").asText()).endsWith("Z");
        assertThat(response.at("/data/user/updatedAt").asText()).endsWith("Z");
        assertThat(response.at("/data/expiresIn").asInt()).isEqualTo(7200);
        assertThat(response.at("/data/refreshExpiresIn").asInt()).isEqualTo(2592000);
        assertThat(jdbcTemplate.queryForObject("select count(*) from companies", Integer.class))
                .isEqualTo(companiesBefore);
        String storedPassword = jdbcTemplate.queryForObject("select password_hash from users where email = ?",
                String.class, email);
        assertThat(storedPassword).isNotEqualTo("StrongPass123!").startsWith("$2");
        assertNoPlainTokensStored(response);
    }

    @Test
    void recruiterRegistrationCreatesCompanyAndAdminMembership() throws Exception {
        String email = uniqueEmail("recruiter");
        String body = """
                {"role":"RECRUITER","companyName":"Example Labs","fullName":"Recruiter One",
                 "email":"%s","password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                """.formatted(email);
        String responseBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("RECRUITER"))
                .andExpect(jsonPath("$.data.user.company.name").value("Example Labs"))
                .andExpect(jsonPath("$.data.user.company.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.user.company.version").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        String userId = response.at("/data/user/userId").asText();
        String companyId = response.at("/data/user/company/companyId").asText();
        assertThat(jdbcTemplate.queryForObject("select count(*) from company_members where user_id = ? and company_id = ? and member_role = 'ADMIN'",
                Integer.class, userId, companyId)).isEqualTo(1);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String email = uniqueEmail("duplicate");
        registerCandidate(email, "StrongPass123!");
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(candidateBody(email, "AnotherPass123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.error.fieldErrors").isMap())
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void adminAndUnknownRolesCannotRegister() throws Exception {
        for (String role : new String[]{"ADMIN", "SUPERUSER"}) {
            String body = candidateBody(uniqueEmail(role.toLowerCase()), "StrongPass123!")
                    .replace("CANDIDATE", role);
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors.role").exists());
        }
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        String email = uniqueEmail("login");
        registerCandidate(email, "StrongPass123!");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "StrongPass123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    @Test
    void wrongPasswordAndUnknownAccountReturnUnauthorized() throws Exception {
        String email = uniqueEmail("wrong-password");
        registerCandidate(email, "StrongPass123!");
        for (String body : new String[]{loginBody(email, "WrongPass123!"),
                loginBody(uniqueEmail("missing"), "StrongPass123!")}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error.fieldErrors").isMap());
        }
    }

    @Test
    void missingCredentialFieldsUseDeclaredUnauthorizedResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-password@example.com\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void refreshRotatesTokenAndRejectsReplay() throws Exception {
        JsonNode registered = registerCandidate(uniqueEmail("rotate"), "StrongPass123!");
        String oldRefresh = registered.at("/data/refreshToken").asText();
        String refreshedBody = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andReturn().getResponse().getContentAsString();
        String newRefresh = objectMapper.readTree(refreshedBody).at("/data/refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode registered = registerCandidate(uniqueEmail("logout"), "StrongPass123!");
        String access = registered.at("/data/accessToken").asText();
        String refresh = registered.at("/data/refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(refresh)))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("X-Request-Id"));
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresAccessTokenAndChecksRefreshOwnership() throws Exception {
        JsonNode first = registerCandidate(uniqueEmail("owner-one"), "StrongPass123!");
        JsonNode second = registerCandidate(uniqueEmail("owner-two"), "StrongPass123!");
        String firstRefresh = first.at("/data/refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + second.at("/data/accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(firstRefresh)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void validationErrorMatchesContractAndPropagatesRequestId() throws Exception {
        String requestId = "req_client_validation_1";
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateBody("not-an-email", "short")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Request validation failed"))
                .andExpect(jsonPath("$.error.fieldErrors.email").exists())
                .andExpect(jsonPath("$.error.fieldErrors.password").exists())
                .andExpect(jsonPath("$.error.requestId").value(requestId));
    }

    @Test
    void candidateCannotSendRecruiterOnlyFieldAndUnknownFieldsAreRejected() throws Exception {
        String candidateWithCompany = candidateBody(uniqueEmail("candidate-company"), "StrongPass123!")
                .replace("\"fullName\"", "\"companyName\":\"Nope\",\"fullName\"");
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(candidateWithCompany))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.companyName").exists());

        String unknownField = candidateBody(uniqueEmail("unknown-field"), "StrongPass123!")
                .replace("}", ",\"admin\":true}");
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(unknownField))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.requestId", matchesPattern("req_.+")));
    }

    @Test
    void flywayCreatedAllAuthTablesInEmptyDatabase() {
        for (String table : new String[]{"users", "companies", "company_members", "refresh_tokens"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables where lower(table_schema) = 'public' and lower(table_name) = ?",
                    Integer.class, table);
            assertThat(count).isEqualTo(1);
        }
    }

    private JsonNode registerCandidate(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(candidateBody(email, password)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assertNoPlainTokensStored(JsonNode response) {
        String refresh = response.at("/data/refreshToken").asText();
        String access = response.at("/data/accessToken").asText();
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_tokens where token_hash in (?, ?)",
                Integer.class, refresh, access)).isZero();
        String storedHash = jdbcTemplate.queryForObject("select token_hash from refresh_tokens where user_id = ?",
                String.class, response.at("/data/user/userId").asText());
        assertThat(storedHash).hasSize(64).doesNotContain(refresh).doesNotContain(access);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String candidateBody(String email, String password) {
        return """
                {"role":"CANDIDATE","fullName":"Candidate One","email":"%s",
                 "password":"%s","acceptedTermsVersion":"2026-08"}
                """.formatted(email, password);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String refreshBody(String refresh) throws Exception {
        return new ObjectMapper().createObjectNode().put("refreshToken", refresh).toString();
    }
}
