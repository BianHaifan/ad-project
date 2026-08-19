package com.adproject.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.domain.InterviewMode;
import com.adproject.application.domain.InterviewStatus;
import com.adproject.application.domain.MeetingProvider;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.application.infrastructure.InterviewEntity;
import com.adproject.application.infrastructure.InterviewRepository;
import com.adproject.application.infrastructure.ResumeSnapshotEntity;
import com.adproject.application.infrastructure.ResumeSnapshotRepository;
import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.CompanyMemberRole;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.integration.google.MeetingProvisioningPort;
import com.adproject.integration.google.MeetingSyncOutcome;
import com.adproject.integration.google.MeetingSyncResult;
import com.adproject.integration.google.ProvisionOutcome;
import com.adproject.integration.google.ProvisionResult;
import com.adproject.job.domain.EmploymentType;
import com.adproject.job.domain.JobStatus;
import com.adproject.job.domain.SalaryCurrency;
import com.adproject.job.domain.SalaryPeriod;
import com.adproject.job.domain.Visibility;
import com.adproject.job.domain.WorkplaceType;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HrAgentRunIntegrationTest {
    private static final ObjectMapper STUB_MAPPER = new ObjectMapper();
    private static final HttpServer STUBS = startStubs();
    private static final Pattern APPLICATION_ID = Pattern.compile("applicationId=([0-9a-fA-F-]{36})");
    private static final Pattern JSON_STRING = Pattern.compile("\"timezone\":\"([^\"]*)\"");
    private static final Pattern JOB_ID = Pattern.compile("\"jobId\":\"([^\"]*)\"");
    private static final AtomicInteger WORLD_SEQUENCE = new AtomicInteger();
    private static volatile String LAST_SCREENING_AUTH_HEADER;

    @DynamicPropertySource
    static void agentProperties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + STUBS.getAddress().getPort();
        registry.add("app.agent.planner-base-url", () -> base);
        registry.add("app.agent.preview-ttl-seconds", () -> 900);
        registry.add("app.deepseek.base-url", () -> base);
        registry.add("app.deepseek.api-key", () -> "test-key");
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;
    @Autowired ResumeSnapshotRepository snapshots;
    @Autowired ApplicationRepository applications;
    @Autowired InterviewRepository interviews;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MeetingProvisioningPort meetingProvisioning;

    @AfterAll
    static void stopStubs() {
        STUBS.stop(0);
    }

    /** The screening pool reads every resume, so each test starts from an empty pool. */
    @BeforeEach
    void cleanScreeningPool() {
        jdbc.update("delete from message_attachments");
        jdbc.update("delete from conversation_read_states");
        jdbc.update("delete from messages");
        jdbc.update("delete from conversations");
        jdbc.update("delete from interview_audit_events");
        jdbc.update("delete from interviews");
        jdbc.update("delete from application_status_events");
        jdbc.update("delete from idempotency_records");
        jdbc.update("delete from applications");
        jdbc.update("delete from resume_snapshots");
        jdbc.update("delete from resumes");
    }

    @Test
    void screenRanksEveryCandidateAndPersistsSafeSteps() throws Exception {
        World world = world();
        JsonNode run = run(world, "帮我筛选一下后端工程师", null, null, "Asia/Shanghai");

        assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.path("confirmationStatus").asText()).isEqualTo("NOT_REQUIRED");
        assertThat(run.at("/target/type").asText()).isEqualTo("JOB");
        assertThat(run.at("/target/id").asText()).isEqualTo(world.jobId());
        assertThat(run.path("result").isNull()).isTrue();
        assertThat(run.path("preview").isNull()).isTrue();
        assertThat(run.at("/screening/jobId").asText()).isEqualTo(world.jobId());
        assertThat(run.at("/screening/jobTitle").asText()).isEqualTo("后端工程师");
        assertThat(run.at("/screening/ranked").size()).isEqualTo(4);
        assertThat(run.at("/screening/ranked/0/fullName").asText()).isEqualTo("张三");
        assertThat(run.at("/screening/ranked/0/rank").asInt()).isEqualTo(1);
        assertThat(run.at("/screening/ranked/0/applicationStatus").asText()).isEqualTo("IN_REVIEW");
        assertThat(run.at("/screening/ranked/0/applicationId").asText())
                .isEqualTo(world.applicants().get(0).applicationId());
        assertThat(run.at("/screening/ranked/0/strongMatches/0").asText()).isEqualTo("fits the role");
        assertThat(run.at("/screening/ranked/0/recommendation").asText())
                .isEqualTo("Top recommendation for this role.");
        assertThat(run.at("/screening/ranked/1/recommendation").isNull()).isTrue();
        assertThat(run.at("/screening/ranked/1/fullName").asText()).isEqualTo("李四");
        assertThat(run.at("/screening/ranked/2/applicationStatus").asText()).isEqualTo("APPLIED");
        assertThat(run.at("/screening/ranked/3/fullName").asText()).isEqualTo("王五");
        assertThat(run.at("/screening/ranked/3/applicationId").isNull()).isTrue();
        assertThat(run.at("/screening/ranked/3/applicationStatus").isNull()).isTrue();
        assertThat(run.path("message").asText()).contains("Screening:", "1. 张三(IN_REVIEW, applicationId=",
                "4. 王五(not applied)");
        assertThat(run.path("steps").size()).isEqualTo(2);
        assertThat(run.at("/steps/1/tool").asText()).isEqualTo("screen_applicants");
        assertThat(run.at("/steps/1/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(run.at("/steps/1/outputSummary").asText()).isEqualTo("ranked=4");
        assertThat(LAST_SCREENING_AUTH_HEADER).isEqualTo("Bearer test-key");

        String audit = String.join(" ", jdbc.query(
                "select input_summary, output_summary from agent_steps where run_id=? order by sequence_no",
                (result, row) -> String.valueOf(result.getString(1)) + String.valueOf(result.getString(2)),
                run.path("runId").asText()));
        assertThat(audit).doesNotContain("Secret resume text", "test-key");
    }

    @Test
    void screenByJobIdContextStoresJobOnRun() throws Exception {
        World world = world();
        JsonNode run = run(world, "screen by id", null, world.jobId(), null);

        assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.at("/screening/jobTitle").asText()).isEqualTo("后端工程师");
        assertThat(jdbc.queryForObject("select job_id from agent_runs where id=?",
                String.class, run.path("runId").asText())).isEqualTo(world.jobId());
    }

    @Test
    void screenClarifiesWhenNoJobMatchesOrManyMatch() throws Exception {
        World world = world();
        JsonNode none = run(world, "screen none", null, null, null);
        assertThat(none.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(none.path("message").asText()).contains("No job of yours matched");
        assertThat(none.at("/steps/1/errorCode").asText()).isEqualTo("AGENT_ARGUMENT_INVALID");

        JsonNode many = run(world, "screen many", null, null, null);
        assertThat(many.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(many.path("message").asText()).contains("Multiple jobs matched", "高级工程师", "初级工程师");
    }

    @Test
    void screeningProviderFailuresProduceSafeErrorCodes() throws Exception {
        World world = world();
        JsonNode httpError = run(world, "screen fail", null, null, null);
        assertFailedScreening(httpError, "http_500");

        JsonNode badJson = run(world, "screen bad json", null, null, null);
        assertFailedScreening(badJson, "invalid_response");

        JsonNode truncated = run(world, "screen truncated", null, null, null);
        assertFailedScreening(truncated, "incomplete_response");
    }

    private void assertFailedScreening(JsonNode run, String code) {
        assertThat(run.path("status").asText()).isEqualTo("FAILED");
        assertThat(run.path("errorCode").asText()).isEqualTo(code);
        assertThat(run.path("message").asText()).isEqualTo("The resume screening service failed. Please try again.");
        assertThat(run.at("/steps/1/errorCode").asText()).isEqualTo(code);
    }

    @Test
    void chatNeedsClarificationAndPlannerFailuresAreSafe() throws Exception {
        World world = world();
        JsonNode chat = run(world, "hello", null, null, null);
        assertThat(chat.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(chat.path("message").asText()).contains("Hello");

        JsonNode clarification = run(world, "unsupported", null, null, null);
        assertThat(clarification.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(clarification.path("message").asText()).isEqualTo("This instruction is not supported yet.");

        JsonNode unavailable = run(world, "planner unavailable", null, null, null);
        assertThat(unavailable.path("status").asText()).isEqualTo("FAILED");
        assertThat(unavailable.path("errorCode").asText()).isEqualTo("AGENT_PLANNER_UNAVAILABLE");

        JsonNode malicious = run(world, "malicious", null, null, null);
        assertThat(malicious.path("status").asText()).isEqualTo("FAILED");
        assertThat(malicious.path("errorCode").asText()).isEqualTo("AGENT_PLAN_REJECTED");
    }

    @Test
    void recruiterNeedsCompanyAndCandidatesCannotUseRecruiterTools() throws Exception {
        Instant now = Instant.parse("2026-08-18T08:00:00Z");
        UserEntity loner = users.save(user("Loner Recruiter", UserRole.RECRUITER, now));
        mvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(loner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"帮我筛选一下后端工程师\"}"))
                .andExpect(status().isForbidden());

        World world = world();
        Applicant candidate = world.applicants().get(0);
        JsonNode rejected = candidateRun(candidate, "帮我筛选一下后端工程师");
        assertThat(rejected.path("status").asText()).isEqualTo("FAILED");
        assertThat(rejected.path("errorCode").asText()).isEqualTo("AGENT_PLAN_REJECTED");

        JsonNode own = candidateRun(candidate, "年龄改成 28");
        assertThat(own.path("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(own.at("/target/type").asText()).isEqualTo("RESUME");
    }

    @Test
    void screeningIsIsolatedToTheRecruitersCompany() throws Exception {
        World owner = world();
        World stranger = world();
        JsonNode selector = run(stranger, "screen none", null, null, null);
        assertThat(selector.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(selector.path("message").asText()).contains("No job of yours matched");

        JsonNode byId = run(stranger, "screen by id", null, owner.jobId(), null);
        assertThat(byId.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(byId.path("message").asText()).contains("does not belong to your company");
    }

    @Test
    void fullInterviewLifecycleScreensSchedulesReschedulesAndCancels() throws Exception {
        World world = world();
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-agent", "https://meet.google.com/agent-abc", null));
        when(meetingProvisioning.updateMeeting(any())).thenReturn(
                new MeetingSyncResult(MeetingSyncOutcome.SYNCED, null));
        when(meetingProvisioning.cancelMeeting(any())).thenReturn(
                new MeetingSyncResult(MeetingSyncOutcome.SYNCED, null));

        JsonNode screening = run(world, "帮我筛选一下后端工程师", null, null, "Asia/Shanghai");
        String conversationId = screening.path("conversationId").asText();
        String applicationId = world.applicants().get(0).applicationId();

        JsonNode schedule = run(world, "安排第一名面试", conversationId, null, "Asia/Shanghai");
        assertThat(schedule.path("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(schedule.at("/target/type").asText()).isEqualTo("APPLICATION");
        assertThat(schedule.at("/target/id").asText()).isEqualTo(applicationId);
        assertThat(schedule.at("/preview/expectedVersion").asInt()).isEqualTo(2);
        assertThat(schedule.at("/preview/changes/0/field").asText()).isEqualTo("scheduledAt");
        assertThat(schedule.at("/preview/changes/0/newValue").asText()).isEqualTo("2026-08-21T07:00:00Z");
        assertThat(schedule.at("/preview/changes/1/newValue").asText()).isEqualTo("Asia/Shanghai");
        assertThat(schedule.at("/preview/changes/2/newValue").asInt()).isEqualTo(60);
        assertThat(schedule.at("/preview/changes/3/newValue").asText()).isEqualTo("ONLINE");

        JsonNode confirmed = confirm(world, schedule);
        assertThat(confirmed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(confirmed.at("/result/operation").asText()).isEqualTo("SCHEDULE_INTERVIEW");
        assertThat(confirmed.at("/result/newVersion").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from applications where id=?",
                String.class, applicationId)).isEqualTo("INTERVIEW");
        String interviewId = jdbc.queryForObject("select id from interviews where application_id=?",
                String.class, applicationId);
        assertThat(jdbc.queryForObject("select meeting_sync_status from interviews where id=?",
                String.class, interviewId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("select location_or_meeting_url from interviews where id=?",
                String.class, interviewId)).isEqualTo("https://meet.google.com/agent-abc");

        JsonNode reschedule = run(world, "改到明天上午九点", conversationId, null, "Asia/Shanghai");
        assertThat(reschedule.path("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(reschedule.at("/target/type").asText()).isEqualTo("INTERVIEW");
        assertThat(reschedule.at("/target/id").asText()).isEqualTo(interviewId);
        assertThat(reschedule.at("/preview/expectedVersion").asInt()).isEqualTo(2);
        assertThat(reschedule.at("/preview/changes/0/oldValue").asText()).isEqualTo("2026-08-21T07:00:00Z");
        assertThat(reschedule.at("/preview/changes/0/newValue").asText()).isEqualTo("2026-08-22T01:00:00Z");

        JsonNode rescheduled = confirm(world, reschedule);
        assertThat(rescheduled.at("/result/operation").asText()).isEqualTo("RESCHEDULE_INTERVIEW");
        assertThat(rescheduled.at("/result/newVersion").asInt()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select scheduled_at from interviews where id=?",
                Instant.class, interviewId)).isEqualTo(Instant.parse("2026-08-22T01:00:00Z"));
        assertThat(jdbc.queryForObject("select location_or_meeting_url from interviews where id=?",
                String.class, interviewId)).isEqualTo("https://meet.google.com/agent-abc");
        verify(meetingProvisioning).updateMeeting(any());

        JsonNode cancel = run(world, "取消这场面试", conversationId, null, null);
        assertThat(cancel.path("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(cancel.at("/preview/expectedVersion").asInt()).isEqualTo(4);
        assertThat(cancel.at("/preview/changes/0/field").asText()).isEqualTo("status");
        assertThat(cancel.at("/preview/changes/0/oldValue").asText()).isEqualTo("SCHEDULED");
        assertThat(cancel.at("/preview/changes/0/newValue").asText()).isEqualTo("CANCELLED");

        JsonNode cancelled = confirm(world, cancel);
        assertThat(cancelled.at("/result/operation").asText()).isEqualTo("CANCEL_INTERVIEW");
        assertThat(jdbc.queryForObject("select status from interviews where id=?",
                String.class, interviewId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select location_or_meeting_url from interviews where id=?",
                String.class, interviewId)).isNull();
        verify(meetingProvisioning).cancelMeeting(any());
    }

    @Test
    void scheduleSecondCandidateWithCustomDurationAndUtcTimezone() throws Exception {
        World world = world();
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-li", "https://meet.google.com/li-abc", null));

        JsonNode screening = run(world, "帮我筛选一下后端工程师", null, null, null);
        JsonNode schedule = run(world, "安排第二名面试，时长45分钟",
                screening.path("conversationId").asText(), null, null);

        assertThat(schedule.at("/target/id").asText()).isEqualTo(world.applicants().get(1).applicationId());
        assertThat(schedule.at("/preview/changes/1/newValue").asText()).isEqualTo("UTC");
        assertThat(schedule.at("/preview/changes/2/newValue").asInt()).isEqualTo(45);

        JsonNode confirmed = confirm(world, schedule);
        assertThat(confirmed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select duration_minutes from interviews where application_id=?",
                Integer.class, world.applicants().get(1).applicationId())).isEqualTo(45);
        assertThat(jdbc.queryForObject("select timezone from interviews where application_id=?",
                String.class, world.applicants().get(1).applicationId())).isEqualTo("UTC");
    }

    @Test
    void scheduleClarifiesForNonApplicantsUnknownAppsExistingInterviewsModesAndStatus() throws Exception {
        World world = world();
        String conversationId = run(world, "帮我筛选一下后端工程师", null, null, null)
                .path("conversationId").asText();

        JsonNode nonApplicant = run(world, "安排第四名面试", conversationId, null, null);
        assertThat(nonApplicant.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(nonApplicant.path("message").asText()).contains("has not applied");

        JsonNode unknown = run(world, "schedule bogus app", conversationId, null, null);
        assertThat(unknown.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(unknown.path("message").asText()).contains("does not exist");

        JsonNode onsite = run(world, "schedule onsite", conversationId, null, null);
        assertThat(onsite.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(onsite.path("message").asText()).contains("only schedule online");

        seedInterview(world.applicants().get(0).applicationId(), Instant.parse("2026-08-18T08:00:00Z"));
        JsonNode exists = run(world, "already scheduled", conversationId, null, null);
        assertThat(exists.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(exists.path("message").asText()).contains("already has a scheduled interview");

        JsonNode notInReview = run(world, "安排第三名面试", conversationId, null, null);
        assertThat(notInReview.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(notInReview.path("message").asText()).contains("in review");
    }

    @Test
    void confirmEnforcesOwnershipVersionConfirmationAndIdempotency() throws Exception {
        World world = world();
        World stranger = world();
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-agent", "https://meet.google.com/agent-abc", null));

        String conversationId = run(world, "帮我筛选一下后端工程师", null, null, null)
                .path("conversationId").asText();
        JsonNode schedule = run(world, "安排第一名面试", conversationId, null, null);
        String runId = schedule.path("runId").asText();
        String applicationId = world.applicants().get(0).applicationId();

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", "Bearer " + stranger.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(schedule)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationId\":\"wrong\",\"expectedRunVersion\":"
                                + schedule.path("version").asInt() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_CONFIRMATION_MISMATCH"));

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationId\":\"" + schedule.at("/preview/confirmationId").asText()
                                + "\",\"expectedRunVersion\":999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_RUN_VERSION_CONFLICT"));

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(schedule)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors['Idempotency-Key']").exists());

        String idempotencyKey = UUID.randomUUID().toString();
        confirmWithKey(world, schedule, idempotencyKey);
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isEqualTo(1);

        JsonNode replay = confirmWithKey(world, schedule, idempotencyKey);
        assertThat(replay.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isEqualTo(1);

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(schedule)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_CONFIRMATION_ALREADY_USED"));
    }

    @Test
    void confirmAfterCancelOrExpiryIsRejected() throws Exception {
        World world = world();
        String conversationId = run(world, "帮我筛选一下后端工程师", null, null, null)
                .path("conversationId").asText();
        String applicationId = world.applicants().get(0).applicationId();

        JsonNode cancelledRun = run(world, "安排第一名面试", conversationId, null, null);
        mvc.perform(post("/api/v1/agent/runs/{runId}/cancel", cancelledRun.path("runId").asText())
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", cancelledRun.path("runId").asText())
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(cancelledRun)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_RUN_NOT_CONFIRMABLE"));

        JsonNode expiredRun = run(world, "安排第一名面试", conversationId, null, null);
        jdbc.update("update agent_runs set preview_expires_at=? where id=?",
                Instant.parse("2026-08-01T00:00:00Z"), expiredRun.path("runId").asText());
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", expiredRun.path("runId").asText())
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(expiredRun)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.confirmationStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.errorCode").value("AGENT_CONFIRMATION_EXPIRED"));

        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isZero();
    }

    @Test
    void executionConflictsRejectTheRunSafely() throws Exception {
        World world = world();
        when(meetingProvisioning.provision(any())).thenReturn(new ProvisionResult(
                ProvisionOutcome.READY, "evt-agent", "https://meet.google.com/agent-abc", null));
        String conversationId = run(world, "帮我筛选一下后端工程师", null, null, null)
                .path("conversationId").asText();
        String applicationId = world.applicants().get(0).applicationId();

        JsonNode staleRun = run(world, "安排第一名面试", conversationId, null, null);
        jdbc.update("update applications set version=3 where id=?", applicationId);
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", staleRun.path("runId").asText())
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(staleRun)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isZero();
        assertThat(jdbc.queryForObject("select status from applications where id=?",
                String.class, applicationId)).isEqualTo("IN_REVIEW");

        JsonNode duplicateRun = run(world, "安排第一名面试", conversationId, null, null);
        seedInterview(applicationId, Instant.parse("2026-08-18T08:00:00Z"));
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", duplicateRun.path("runId").asText())
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(duplicateRun)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("INTERVIEW_ALREADY_EXISTS"));
        assertThat(jdbc.queryForObject("select count(*) from interviews where application_id=?",
                Integer.class, applicationId)).isEqualTo(1);
    }

    @Test
    void conversationEndpointsAndCrossRoleAccess() throws Exception {
        World world = world();
        World stranger = world();
        JsonNode screening = run(world, "帮我筛选一下后端工程师", null, null, null);
        String conversationId = screening.path("conversationId").asText();
        run(world, "hello", null, null, null);

        mvc.perform(get("/api/v1/agent/conversations")
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(get("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runs.length()").value(1))
                .andExpect(jsonPath("$.data.runs[0].instruction").value("帮我筛选一下后端工程师"))
                // The screening pool spans both fixture worlds (4 resumes each).
                .andExpect(jsonPath("$.data.runs[0].screening.ranked.length()").value(8));

        mvc.perform(get("/api/v1/agent/conversations/recent")
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty());

        mvc.perform(get("/api/v1/agent/runs/{runId}", screening.path("runId").asText())
                        .header("Authorization", "Bearer " + world.applicants().get(0).token()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/agent/runs/{runId}", screening.path("runId").asText())
                        .header("Authorization", "Bearer " + stranger.recruiterToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletesAnOwnedConversationWithRunsAndSteps() throws Exception {
        World world = world();
        World stranger = world();
        JsonNode screening = run(world, "帮我筛选一下后端工程师", null, null, null);
        String conversationId = screening.path("conversationId").asText();
        String runId = screening.path("runId").asText();
        run(world, "hello", null, null, null);

        // Another recruiter's conversation is hidden, not deletable.
        mvc.perform(delete("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", "Bearer " + stranger.recruiterToken()))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from agent_steps where run_id=?",
                Integer.class, runId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from agent_runs where id=?",
                Integer.class, runId)).isZero();

        mvc.perform(get("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/agent/conversations")
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // Deleting again (or a missing id) stays a safe 404.
        mvc.perform(delete("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/agent/conversations/{conversationId}", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recruiterCannotTouchCandidateAgentRuns() throws Exception {
        World world = world();
        Applicant candidate = world.applicants().get(0);
        JsonNode candidateRun = candidateRun(candidate, "年龄改成 28");
        String runId = candidateRun.path("runId").asText();

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(candidateRun)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .header("Authorization", "Bearer " + world.recruiterToken()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JsonNode run(World world, String instruction, String conversationId, String jobId,
                         String timezone) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("instruction", instruction);
        if (conversationId != null) request.put("conversationId", conversationId);
        if (jobId != null) request.put("jobId", jobId);
        if (timezone != null) request.put("timezone", timezone);
        String body = mvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private JsonNode candidateRun(Applicant candidate, String instruction) throws Exception {
        String body = mvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"" + instruction + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private JsonNode confirm(World world, JsonNode run) throws Exception {
        return confirmWithKey(world, run, UUID.randomUUID().toString());
    }

    private JsonNode confirmWithKey(World world, JsonNode run, String idempotencyKey) throws Exception {
        String body = mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", run.path("runId").asText())
                        .header("Authorization", "Bearer " + world.recruiterToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(run)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private String confirmBody(JsonNode run) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "confirmationId", run.at("/preview/confirmationId").asText(),
                "expectedRunVersion", run.path("version").asInt()));
    }

    private void seedInterview(String applicationId, Instant now) {
        interviews.save(new InterviewEntity(UUID.randomUUID().toString(), applicationId,
                Instant.parse("2026-08-21T07:00:00Z"), "Asia/Shanghai", 60, InterviewMode.ONLINE,
                null, null, InterviewStatus.SCHEDULED, MeetingProvider.GOOGLE_MEET, now));
    }

    private World world() {
        // Each world gets strictly older timestamps than the next one, so the screening pool
        // (updatedAt DESC, reversed by the stub into rank order) ranks the FIRST world's 张三
        // as #1 even when several worlds share the pool.
        Instant now = Instant.parse("2026-08-18T08:00:00Z")
                .minusSeconds(1_000_000L - WORLD_SEQUENCE.incrementAndGet() * 100L);
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Acme",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(),
                recruiter.getId(), CompanyMemberRole.ADMIN, now));
        String jobId = job(company, recruiter, "后端工程师", now);
        String secondJobId = job(company, recruiter, "高级工程师", now);
        String thirdJobId = job(company, recruiter, "初级工程师", now);
        String failJobId = job(company, recruiter, "Fail Screening Job", now);
        String badJsonJobId = job(company, recruiter, "Bad Json Job", now);
        String truncatedJobId = job(company, recruiter, "Truncated Job", now);

        Applicant zhang = applicant("张三", jobId, ApplicationStatus.IN_REVIEW, 2, now.minusSeconds(3));
        Applicant li = applicant("李四", jobId, ApplicationStatus.IN_REVIEW, 2, now.minusSeconds(2));
        Applicant zhao = applicant("赵六", jobId, ApplicationStatus.APPLIED, 1, now.minusSeconds(1));
        Applicant wang = candidateOnly("王五", now);
        return new World(jwt.createAccessToken(recruiter), recruiter.getId(), company.getId(), jobId,
                secondJobId, thirdJobId, failJobId, badJsonJobId, truncatedJobId,
                List.of(zhang, li, zhao, wang));
    }

    private Applicant applicant(String name, String jobId, ApplicationStatus status, int version,
                                Instant resumeUpdatedAt) {
        UserEntity candidate = users.save(user(name, UserRole.CANDIDATE, resumeUpdatedAt));
        Applicant resumeHolder = resume(candidate, name, resumeUpdatedAt);
        String snapshotId = UUID.randomUUID().toString();
        snapshots.save(new ResumeSnapshotEntity(snapshotId, resumeHolder.resumeId(), candidate.getId(),
                name, 27, "Shanghai", "Engineer", "Secret resume text for " + name, "[]", "[\"Java\"]",
                1, resumeUpdatedAt, resumeUpdatedAt, resumeUpdatedAt));
        String applicationId = UUID.randomUUID().toString();
        applications.save(new ApplicationEntity(applicationId, jobId, candidate.getId(),
                resumeHolder.resumeId(), snapshotId, candidate.getEmail(), true, status,
                resumeUpdatedAt, resumeUpdatedAt, version));
        return new Applicant(jwt.createAccessToken(candidate), candidate.getId(),
                resumeHolder.resumeId(), applicationId, name);
    }

    private Applicant candidateOnly(String name, Instant resumeUpdatedAt) {
        UserEntity candidate = users.save(user(name, UserRole.CANDIDATE, resumeUpdatedAt));
        Applicant holder = resume(candidate, name, resumeUpdatedAt);
        return new Applicant(jwt.createAccessToken(candidate), candidate.getId(),
                holder.resumeId(), null, name);
    }

    private Applicant resume(UserEntity candidate, String name, Instant resumeUpdatedAt) {
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), name, 27, "Shanghai", "Engineer",
                "Secret resume text for " + name, "[]", "[\"Java\"]", 1, resumeUpdatedAt, resumeUpdatedAt));
        return new Applicant(null, candidate.getId(), resumeId, null, name);
    }

    private UserEntity user(String name, UserRole role, Instant now) {
        String id = UUID.randomUUID().toString();
        return new UserEntity(id, id + "@example.com", "hash", name, role, UserStatus.ACTIVE,
                "2026-08", now, now);
    }

    private String job(CompanyEntity company, UserEntity recruiter, String title, Instant now) {
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, company.getId(), recruiter.getId(), recruiter.getId(), title,
                EmploymentType.FULL_TIME, WorkplaceType.HYBRID, "Shanghai", 5000, 8000,
                SalaryCurrency.SGD, SalaryPeriod.MONTH, title + " description", "[]", "[]", null,
                Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1, now, now));
        return id;
    }

    // ------------------------------------------------------------------
    // HTTP stubs for the planner and the screening model
    // ------------------------------------------------------------------

    private static HttpServer startStubs() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/v1/agent/plan", HrAgentRunIntegrationTest::respondWithPlan);
            server.createContext("/chat/completions", HrAgentRunIntegrationTest::respondWithChatCompletion);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respondWithPlan(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("planner unavailable")) {
            send(exchange, 503, "{\"error\":\"down\"}");
            return;
        }
        if (body.contains("\"agentType\":\"CANDIDATE\"")) {
            if (body.contains("帮我筛选一下后端工程师")) {
                send(exchange, 200, screenPlan("后端工程师"));
                return;
            }
            send(exchange, 200, """
                    {"status":"READY","intent":"UPDATE_RESUME","target":"DEFAULT_RESUME","operations":[
                     {"tool":"get_my_resume","arguments":{}},
                     {"tool":"preview_resume_patch","arguments":{"field":"age","action":"set","value":28}}],
                     "message":"Review the proposed resume change."}
                    """);
            return;
        }
        String instruction = instruction(body);
        if (instruction.contains("hello")) {
            send(exchange, 200, """
                    {"status":"CHAT","intent":"CHAT","target":null,"operations":[],
                     "message":"Hello! How can I help with your recruiting work?"}
                    """);
        } else if (instruction.contains("unsupported")) {
            send(exchange, 200, """
                    {"status":"NEEDS_CLARIFICATION","intent":null,"target":null,"operations":[],
                     "message":"This instruction is not supported yet."}
                    """);
        } else if (instruction.contains("malicious")) {
            send(exchange, 200, """
                    {"status":"READY","intent":"SCREEN_APPLICANTS","target":null,"operations":[
                     {"tool":"disable_user","arguments":{}}],
                     "message":"Attempted unsafe plan."}
                    """);
        } else if (instruction.contains("screen by id")) {
            Matcher jobId = JOB_ID.matcher(body);
            send(exchange, 200, """
                    {"status":"READY","intent":"SCREEN_APPLICANTS","target":null,"operations":[
                     {"tool":"screen_applicants","arguments":{"jobId":"%s"}}],
                     "message":"Screening the job."}
                    """.formatted(jobId.find() ? jobId.group(1) : ""));
        } else if (instruction.contains("screen none")) {
            send(exchange, 200, screenPlan("不存在的岗位"));
        } else if (instruction.contains("screen many")) {
            send(exchange, 200, screenPlan("工程师"));
        } else if (instruction.contains("screen fail")) {
            send(exchange, 200, screenPlan("Fail Screening Job"));
        } else if (instruction.contains("screen bad json")) {
            send(exchange, 200, screenPlan("Bad Json Job"));
        } else if (instruction.contains("screen truncated")) {
            send(exchange, 200, screenPlan("Truncated Job"));
        } else if (instruction.contains("schedule bogus")) {
            send(exchange, 200, schedulePlan("00000000-0000-0000-0000-00000000dead",
                    "2026-08-21T07:00:00Z", timezone(body), 0, null));
        } else if (instruction.contains("schedule onsite")) {
            send(exchange, 200, schedulePlan(applicationId(body, 1), "2026-08-21T07:00:00Z",
                    timezone(body), 0, "ONSITE"));
        } else if (instruction.contains("already scheduled")) {
            send(exchange, 200, schedulePlan(applicationId(body, 1), "2026-08-21T07:00:00Z",
                    timezone(body), 0, null));
        } else if (instruction.contains("安排第四")) {
            send(exchange, 200, """
                    {"status":"NEEDS_CLARIFICATION","intent":null,"target":null,"operations":[],
                     "message":"This candidate has not applied, so an interview cannot be scheduled."}
                    """);
        } else if (instruction.contains("取消")) {
            send(exchange, 200, """
                    {"status":"READY","intent":"CANCEL_INTERVIEW","target":null,"operations":[
                     {"tool":"cancel_interview","arguments":{"applicationId":"%s"}}],
                     "message":"Review the interview cancellation."}
                    """.formatted(applicationId(body, 1)));
        } else if (instruction.contains("改到")) {
            send(exchange, 200, """
                    {"status":"READY","intent":"RESCHEDULE_INTERVIEW","target":null,"operations":[
                     {"tool":"reschedule_interview","arguments":{"applicationId":"%s",
                      "scheduledAt":"2026-08-22T01:00:00Z","timezone":"%s"}}],
                     "message":"Review the interview reschedule."}
                    """.formatted(applicationId(body, 1), timezone(body)));
        } else if (instruction.contains("安排第")) {
            int occurrence = instruction.contains("第三") ? 3 : instruction.contains("第二") ? 2 : 1;
            int duration = instruction.contains("45") ? 45 : 0;
            send(exchange, 200, schedulePlan(applicationId(body, occurrence), "2026-08-21T07:00:00Z",
                    timezone(body), duration, null));
        } else if (instruction.contains("安排")) {
            send(exchange, 200, schedulePlan(applicationId(body, 1), "2026-08-21T07:00:00Z",
                    timezone(body), 0, null));
        } else if (instruction.contains("帮我筛选一下后端工程师")) {
            send(exchange, 200, screenPlan("后端工程师"));
        } else {
            send(exchange, 200, """
                    {"status":"NEEDS_CLARIFICATION","intent":null,"target":null,"operations":[],
                     "message":"This instruction is not supported yet."}
                    """);
        }
    }

    private static void respondWithChatCompletion(HttpExchange exchange) throws IOException {
        LAST_SCREENING_AUTH_HEADER = exchange.getRequestHeaders().getFirst("Authorization");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String content;
        try {
            JsonNode payload = STUB_MAPPER.readTree(body);
            content = payload.path("messages").get(1).path("content").asText();
        } catch (Exception exception) {
            send(exchange, 500, "{}");
            return;
        }
        JsonNode input;
        try {
            input = STUB_MAPPER.readTree(content);
        } catch (Exception exception) {
            send(exchange, 200, completion("stop", "not json"));
            return;
        }
        String title = input.path("jobTitle").asText();
        if ("Fail Screening Job".equals(title)) {
            send(exchange, 500, "{}");
            return;
        }
        if ("Bad Json Job".equals(title)) {
            send(exchange, 200, completion("stop", "not json"));
            return;
        }
        if ("Truncated Job".equals(title)) {
            send(exchange, 200, completion("length", "{\"ranked\":[],\"message\":\"partial\"}"));
            return;
        }
        List<JsonNode> reversed = new ArrayList<>();
        for (JsonNode candidate : input.path("candidates")) {
            reversed.add(0, candidate);
        }
        StringBuilder ranked = new StringBuilder("[");
        int rank = 1;
        for (JsonNode candidate : reversed) {
            if (rank > 1) ranked.append(',');
            ranked.append("{\"candidateId\":\"").append(candidate.path("candidateId").asText())
                    .append("\",\"applicationId\":")
                    .append(candidate.path("applicationId").isNull()
                            ? "null" : jsonQuote(candidate.path("applicationId").asText()))
                    .append(",\"rank\":").append(rank)
                    .append(",\"strongMatches\":[\"fits the role\"],\"gaps\":[],")
                    .append("\"recommendation\":")
                    .append(rank == 1 ? jsonQuote("Top recommendation for this role.") : "null")
                    .append("}");
            rank++;
        }
        ranked.append(']');
        send(exchange, 200, completion("stop",
                "{\"ranked\":" + ranked + ",\"message\":\"Ranked by resume fit for this role.\"}"));
    }

    private static String screenPlan(String jobSelector) {
        return """
                {"status":"READY","intent":"SCREEN_APPLICANTS","target":null,"operations":[
                 {"tool":"screen_applicants","arguments":{"jobSelector":"%s"}}],
                 "message":"Screening the job."}
                """.formatted(jobSelector);
    }

    private static String schedulePlan(String applicationId, String scheduledAt, String timezone,
                                       int durationMinutes, String mode) {
        StringBuilder arguments = new StringBuilder("{\"applicationId\":\"").append(applicationId)
                .append("\",\"scheduledAt\":\"").append(scheduledAt)
                .append("\",\"timezone\":\"").append(timezone).append('"');
        if (durationMinutes > 0) {
            arguments.append(",\"durationMinutes\":").append(durationMinutes);
        }
        if (mode != null) {
            arguments.append(",\"mode\":\"").append(mode).append('"');
        }
        arguments.append('}');
        return """
                {"status":"READY","intent":"SCHEDULE_INTERVIEW","target":null,"operations":[
                 {"tool":"schedule_interview","arguments":%s}],
                 "message":"Review the proposed interview change."}
                """.formatted(arguments);
    }

    private static String completion(String finishReason, String content) {
        return "{\"choices\":[{\"finish_reason\":\"" + finishReason
                + "\",\"message\":{\"role\":\"assistant\",\"content\":" + jsonQuote(content) + "}}]}";
    }

    private static String instruction(String body) {
        int start = body.indexOf("\"instruction\":\"");
        if (start < 0) return "";
        int value = start + "\"instruction\":\"".length();
        int end = body.indexOf('"', value);
        return end < 0 ? "" : body.substring(value, end);
    }

    private static String applicationId(String body, int occurrence) {
        Matcher matcher = APPLICATION_ID.matcher(body);
        int seen = 0;
        while (matcher.find()) {
            seen++;
            if (seen == occurrence) return matcher.group(1);
        }
        return "00000000-0000-0000-0000-00000000dead";
    }

    private static String timezone(String body) {
        Matcher matcher = JSON_STRING.matcher(body);
        return matcher.find() ? matcher.group(1) : "UTC";
    }

    private static String jsonQuote(String value) {
        try {
            return STUB_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record World(String recruiterToken, String recruiterId, String companyId, String jobId,
                         String secondJobId, String thirdJobId, String failJobId, String badJsonJobId,
                         String truncatedJobId, List<Applicant> applicants) {}

    private record Applicant(String token, String userId, String resumeId, String applicationId,
                             String fullName) {}
}
