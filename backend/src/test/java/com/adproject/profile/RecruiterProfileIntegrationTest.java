package com.adproject.profile;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.CompanyMemberRole;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.profile.infrastructure.RecruiterProfileRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterProfileIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMemberRepository members;
    @Autowired RecruiterProfileRepository profiles;
    @Autowired JwtService jwt;

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");

    private record Account(UserEntity user, CompanyEntity company) {}

    @Test
    void getAndUpdateProfilePersistsEditableFieldsAndKeepsReadOnlyFields() throws Exception {
        Account recruiter = createRecruiter("recruiter-1");

        mvc.perform(get("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(recruiter.user().getId()))
                .andExpect(jsonPath("$.data.fullName").value("Recruiter One"))
                .andExpect(jsonPath("$.data.email").value(recruiter.user().getEmail()))
                .andExpect(jsonPath("$.data.company.companyId").value(recruiter.company().getId()))
                .andExpect(jsonPath("$.data.company.name").value("Example Labs"))
                .andExpect(jsonPath("$.data.title").value(""))
                .andExpect(jsonPath("$.data.bio").value(nullValue()))
                .andExpect(jsonPath("$.data.avatarUrl").value(nullValue()));

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Updated Recruiter","title":"  Head of Engineering  ",
                                 "bio":"  Builds teams  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Recruiter"))
                .andExpect(jsonPath("$.data.title").value("Head of Engineering"))
                .andExpect(jsonPath("$.data.bio").value("Builds teams"))
                .andExpect(jsonPath("$.data.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.email").value(recruiter.user().getEmail()))
                .andExpect(jsonPath("$.data.company.companyId").value(recruiter.company().getId()));

        mvc.perform(get("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Recruiter"))
                .andExpect(jsonPath("$.data.title").value("Head of Engineering"))
                .andExpect(jsonPath("$.data.bio").value("Builds teams"));

        var stored = profiles.findById(recruiter.user().getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getTitle()).isEqualTo("Head of Engineering");
        org.assertj.core.api.Assertions.assertThat(stored.getBio()).isEqualTo("Builds teams");
    }

    @Test
    void profileRequiresAuthenticationAndRecruiterRole() throws Exception {
        mvc.perform(get("/api/v1/recruiter/profile"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/recruiter/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        UserEntity candidate = createCandidate("candidate-1");
        mvc.perform(get("/api/v1/recruiter/profile").header("Authorization", bearer(candidate)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void profileValidatesRequiredTitleAndFieldLengths() throws Exception {
        Account recruiter = createRecruiter("recruiter-2");

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"Engineer\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.title").exists());

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.title").exists());

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Engineer\",\"fullName\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.fullName").exists());

        String longBio = "a".repeat(1001);
        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"bio\":\"" + longBio + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.bio").exists());
    }

    @Test
    void profileRejectsReadOnlyFieldsInsteadOfSilentlyIgnoringThem() throws Exception {
        Account recruiter = createRecruiter("recruiter-3");

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.email").exists());

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"companyId\":\"other-company\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.companyId").exists());

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"avatarUrl\":\"https://example.com/avatar.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors.avatarUrl").exists());
    }

    @Test
    void partialUpdatePreservesFieldsNotSent() throws Exception {
        Account recruiter = createRecruiter("recruiter-4");

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Partial Recruiter\",\"title\":\"Lead Engineer\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/recruiter/profile").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Now has a bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Partial Recruiter"))
                .andExpect(jsonPath("$.data.title").value("Lead Engineer"))
                .andExpect(jsonPath("$.data.bio").value("Now has a bio"));
    }

    private Account createRecruiter(String prefix) {
        UserEntity user = users.save(new UserEntity(UUID.randomUUID().toString(),
                prefix + "-" + UUID.randomUUID() + "@example.com", "hash", "Recruiter One",
                UserRole.RECRUITER, UserStatus.ACTIVE, "2026-08", NOW, NOW));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(),
                "Example Labs", CompanyVerificationStatus.APPROVED, 1, user.getId(), NOW, NOW));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), user.getId(),
                CompanyMemberRole.ADMIN, NOW));
        return new Account(user, company);
    }

    private UserEntity createCandidate(String prefix) {
        return users.save(new UserEntity(UUID.randomUUID().toString(),
                prefix + "-" + UUID.randomUUID() + "@example.com", "hash", "Candidate One",
                UserRole.CANDIDATE, UserStatus.ACTIVE, "2026-08", NOW, NOW));
    }

    private String bearer(Account account) {
        return bearer(account.user());
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(user);
    }
}
