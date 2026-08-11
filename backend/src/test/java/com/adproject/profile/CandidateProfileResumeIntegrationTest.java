package com.adproject.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.adproject.auth.application.JwtService;
import com.adproject.user.domain.*;
import com.adproject.user.infrastructure.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CandidateProfileResumeIntegrationTest {
 @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired JwtService jwt;
 private String token(UserRole role){var now=Instant.parse("2026-08-11T08:00:00Z");var u=users.save(new UserEntity(UUID.randomUUID().toString(),UUID.randomUUID()+"@example.com","hash","Candidate",role,UserStatus.ACTIVE,"2026-08",now,now));return jwt.createAccessToken(u);}
 private static String auth(String t){return "Bearer "+t;}
 @Test void profileProjectsThenUpdatesWithVersionAndRejectsWrongRole() throws Exception {String c=token(UserRole.CANDIDATE);mvc.perform(get("/api/v1/candidate/profile").header("Authorization",auth(c))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1)).andExpect(jsonPath("$.data.stats.applicationCount").value(0));mvc.perform(patch("/api/v1/candidate/profile").header("Authorization",auth(c)).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Updated\",\"headline\":\"Engineer\",\"location\":\"Singapore\",\"expectedVersion\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Updated")).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.updatedAt").value(org.hamcrest.Matchers.endsWith("Z")));mvc.perform(get("/api/v1/candidate/profile").header("Authorization",auth(token(UserRole.RECRUITER)))).andExpect(status().isForbidden());}
 @Test void profileValidationConflictAndAuthenticationErrorsContainRequestId() throws Exception {String c=token(UserRole.CANDIDATE);mvc.perform(patch("/api/v1/candidate/profile").header("Authorization",auth(c)).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":null,\"expectedVersion\":1}")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.requestId").isNotEmpty());mvc.perform(patch("/api/v1/candidate/profile").header("Authorization",auth(c)).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));mvc.perform(get("/api/v1/candidate/profile")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.requestId").isNotEmpty());}
 @Test void resumeMissingCreateReadReplaceAndSingleRow() throws Exception {String c=token(UserRole.CANDIDATE);mvc.perform(get("/api/v1/candidate/resume").header("Authorization",auth(c))).andExpect(status().isNotFound());String body="{\"fullName\":\"Candidate\",\"age\":27,\"location\":\"Singapore\",\"headline\":\"Engineer\",\"summary\":\"Summary\",\"experiences\":[{\"experienceId\":null,\"title\":\"Intern\",\"company\":\"ACME\",\"description\":\"Built APIs\",\"startDate\":\"2025-01\",\"endDate\":null}],\"expectedVersion\":0}";mvc.perform(put("/api/v1/candidate/resume").header("Authorization",auth(c)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1)).andExpect(jsonPath("$.data.experiences[0].title").value("Intern"));mvc.perform(get("/api/v1/candidate/resume").header("Authorization",auth(c))).andExpect(status().isOk());mvc.perform(put("/api/v1/candidate/resume").header("Authorization",auth(c)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());}
 @Test void resumeValidatesRoleAndFields() throws Exception {String body="{\"fullName\":\"C\",\"age\":10,\"location\":\"S\",\"headline\":\"E\",\"summary\":\"S\",\"experiences\":[],\"expectedVersion\":0}";mvc.perform(put("/api/v1/candidate/resume").header("Authorization",auth(token(UserRole.CANDIDATE))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.fieldErrors.age").exists());mvc.perform(get("/api/v1/candidate/resume").header("Authorization",auth(token(UserRole.RECRUITER)))).andExpect(status().isForbidden());}
}
