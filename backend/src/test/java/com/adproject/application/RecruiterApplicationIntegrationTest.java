package com.adproject.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.*;
import com.adproject.company.infrastructure.*;
import com.adproject.job.domain.*;
import com.adproject.job.infrastructure.*;
import com.adproject.recommendation.infrastructure.*;
import com.adproject.resume.infrastructure.*;
import com.adproject.user.domain.*;
import com.adproject.user.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RecruiterApplicationIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired CompanyRepository companies; @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs; @Autowired ResumeRepository resumes; @Autowired ObjectMapper mapper;
    @Autowired CandidateJobRecommendationRepository recommendations;
    @Autowired JdbcTemplate jdbc;

    @Test void listIsCompanyScopedSearchableFilteredPagedCountedAndStable() throws Exception {
        Fixture fixture = fixture("Alice Candidate");
        String first = submit(fixture, job(fixture, "Backend One"));
        String second = submit(fixture, job(fixture, "Backend Two"));
        jdbc.update("update applications set status='IN_REVIEW',version=2 where id=?", second);
        Fixture other = fixture("Other Candidate"); submit(other, job(other, "Other Job"));

        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(fixture))
                        .param("q", "alice").param("page", "1").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(2)).andExpect(jsonPath("$.meta.hasNext").value(true))
                .andExpect(jsonPath("$.meta.counts.applied").value(1))
                .andExpect(jsonPath("$.meta.counts.inReview").value(1))
                .andExpect(jsonPath("$.data[0].candidate.fullName").value("Alice Candidate"))
                .andExpect(jsonPath("$.data[0].matchScore").isEmpty())
                .andExpect(jsonPath("$.data[0].owner").isEmpty());
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(fixture))
                        .param("status", "IN_REVIEW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].applicationId").value(second));
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(fixture))
                        .param("jobId", fixtureJobId(second)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].applicationId").value(second));
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(fixture))
                        .param("sort", "candidateName,desc"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.fieldErrors.sort").exists())
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
        assertThat(first).isNotEqualTo(second);
    }

    @Test void listRequiresRecruiterAuthentication() throws Exception {
        Fixture fixture = fixture("List Role Candidate");
        mvc.perform(get("/api/v1/recruiter/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", candidate(fixture)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test void detailReturnsSnapshotAuditAndSafeOwnership() throws Exception {
        Fixture fixture = fixture("Detail Candidate"); String id = submit(fixture, job(fixture, "Detail Job"));
        String body = mvc.perform(get("/api/v1/recruiter/applications/{id}", id)
                        .header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.resumeSnapshot.fullName").value("Detail Candidate"))
                .andExpect(jsonPath("$.data.timeline[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.notes.length()").value(0))
                .andExpect(jsonPath("$.data.interview").isEmpty())
                .andExpect(jsonPath("$.data.matchAnalysis").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Z");
        Fixture other = fixture("Other");
        mvc.perform(get("/api/v1/recruiter/applications/{id}", id).header("Authorization", recruiter(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/recruiter/applications/{id}", id).header("Authorization", candidate(fixture)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/recruiter/applications/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test void transitionsFollowFrozenMachineVersionAuditAndReachCandidateTimeline() throws Exception {
        Fixture fixture = fixture("Transition Candidate"); String id = submit(fixture, job(fixture, "Transition Job"));
        transition(fixture, id, "IN_REVIEW", 1, 201, null);
        transition(fixture, id, "REJECTED", 2, 201, null);
        transition(fixture, id, "IN_REVIEW", 3, 409, "INVALID_APPLICATION_TRANSITION");
        transition(fixture, id, "REJECTED", 1, 409, "VERSION_CONFLICT");
        mvc.perform(get("/api/v1/candidate/applications/{id}", id).header("Authorization", candidate(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.timeline.length()").value(3));
        assertThat(jdbc.queryForObject("select count(*) from application_status_events where application_id=? " +
                "and actor_id=? and request_id is not null", Integer.class, id, fixture.recruiterId())).isEqualTo(2);
        mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", id)
                        .header("Authorization", candidate(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"IN_REVIEW\",\"reason\":\"No\",\"expectedVersion\":3}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", id)
                        .header("Authorization", recruiter(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"WITHDRAWN\",\"reason\":\"No\",\"expectedVersion\":3}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test void offeredFollowsInterviewIsTerminalAuditedAndGuarded() throws Exception {
        Fixture fixture = fixture("Offer Candidate"); String id = submit(fixture, job(fixture, "Offer Job"));
        transition(fixture, id, "OFFERED", 1, 409, "INVALID_APPLICATION_TRANSITION");
        jdbc.update("update applications set status='INTERVIEW',version=2 where id=?", id);
        transition(fixture, id, "OFFERED", 2, 201, null);
        transition(fixture, id, "REJECTED", 3, 409, "INVALID_APPLICATION_TRANSITION");
        transition(fixture, id, "OFFERED", 2, 409, "VERSION_CONFLICT");
        mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", id)
                        .header("Authorization", candidate(fixture)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"OFFERED\",\"reason\":\"No\",\"expectedVersion\":3}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from application_status_events where application_id=? " +
                "and to_status='OFFERED' and reason='Reviewed candidate' and request_id is not null",
                Integer.class, id)).isEqualTo(1);
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.counts.offered").value(1));
        mvc.perform(get("/api/v1/candidate/applications/{id}", id).header("Authorization", candidate(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OFFERED"));
    }

    @Test void validRecommendationScoreIsReturnedInListAndDetail() throws Exception {
        Fixture fixture = fixture("Scored Candidate");
        String jobId = job(fixture, "Scored Job");
        String id = submit(fixture, jobId);
        recommend(fixture.candidateId(), jobId, 87, 1, 1);
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].matchScore").value(87));
        mvc.perform(get("/api/v1/recruiter/applications/{id}", id).header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.matchScore").value(87))
                .andExpect(jsonPath("$.data.matchAnalysis.score").value(87))
                .andExpect(jsonPath("$.data.matchAnalysis.modelVersion").value("model-v1"))
                .andExpect(jsonPath("$.data.matchAnalysis.strongMatches[0]").value("Go"))
                .andExpect(jsonPath("$.data.matchAnalysis.gaps[0]").value("No ops"))
                .andExpect(jsonPath("$.data.matchAnalysis.evidence[0]").value("5y experience"))
                .andExpect(jsonPath("$.data.matchAnalysis.generatedAt").isNotEmpty());
    }

    @Test void staleRecommendationScoreIsOmitted() throws Exception {
        Fixture fixture = fixture("Stale Candidate");
        String jobId = job(fixture, "Stale Job");
        String id = submit(fixture, jobId);
        recommend(fixture.candidateId(), jobId, 91, 2, 1); // resume version drifted since snapshot was generated
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].matchScore").isEmpty());
        mvc.perform(get("/api/v1/recruiter/applications/{id}", id).header("Authorization", recruiter(fixture)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.matchScore").isEmpty())
                .andExpect(jsonPath("$.data.matchAnalysis").isEmpty());
    }

    @Test void recommendationDoesNotLeakAcrossCompanies() throws Exception {
        Fixture owner = fixture("Owner Candidate");
        String jobId = job(owner, "Owner Job");
        String id = submit(owner, jobId);
        recommend(owner.candidateId(), jobId, 88, 1, 1);
        Fixture other = fixture("Other Recruiter");
        mvc.perform(get("/api/v1/recruiter/applications/{id}", id).header("Authorization", recruiter(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/recruiter/applications").header("Authorization", recruiter(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    private void recommend(String candidateId, String jobId, int score, int resumeVersion, int jobVersion) {
        recommendations.save(new CandidateJobRecommendationEntity(UUID.randomUUID().toString(), candidateId, jobId,
                score, "MODEL", "model-v1", "feature-v1", "[\"Go\"]", "[\"No ops\"]", "[\"5y experience\"]",
                resumeVersion, 0, jobVersion, Instant.parse("2026-08-12T01:00:00Z")));
    }

    private void transition(Fixture f, String id, String target, int version, int status, String code) throws Exception {
        var result = mvc.perform(post("/api/v1/recruiter/applications/{id}/transitions", id)
                .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"" + target + "\",\"reason\":\"Reviewed candidate\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().is(status));
        if (code != null) result.andExpect(jsonPath("$.error.code").value(code));
        else result.andExpect(jsonPath("$.data.application.status").value(target))
                .andExpect(jsonPath("$.data.event.toStatus").value(target));
    }

    private String submit(Fixture f, String jobId) throws Exception {
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resumeId\":\"" + f.resumeId() +
                                "\",\"contactEmail\":\"" + f.email() + "\",\"shareProfile\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/applicationId").asText();
    }

    private String fixtureJobId(String applicationId) {
        return jdbc.queryForObject("select job_id from applications where id=?", String.class, applicationId);
    }

    private Fixture fixture(String candidateName) {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(), CompanyMemberRole.ADMIN, now));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer", "Summary",
                "[]", 1, now, now));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate), recruiter.getId(),
                candidate.getId(), candidate.getEmail(), company.getId(), resumeId);
    }
    private UserEntity user(String name, UserRole role, Instant now) { String id=UUID.randomUUID().toString(); return new UserEntity(id,id+"@example.com","hash",name,role,UserStatus.ACTIVE,"2026-08",now,now); }
    private String job(Fixture f, String title) { Instant now=Instant.parse("2026-08-12T01:00:00Z"); String id=UUID.randomUUID().toString(); jobs.save(new JobEntity(id,f.companyId(),f.recruiterId(),f.recruiterId(),title,EmploymentType.FULL_TIME,WorkplaceType.HYBRID,"Singapore",5000,8000,SalaryCurrency.SGD,SalaryPeriod.MONTH,"Description","[]","[]",null,Visibility.PUBLIC,JobStatus.ACTIVE,0,1,now,now)); return id; }
    private static String recruiter(Fixture f){return "Bearer "+f.recruiterToken();} private static String candidate(Fixture f){return "Bearer "+f.candidateToken();}
    private record Fixture(String recruiterToken,String candidateToken,String recruiterId,String candidateId,String email,String companyId,String resumeId){}
}
