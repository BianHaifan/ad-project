package com.adproject.agent.application;

import com.adproject.agent.api.AgentDtos;
import com.adproject.agent.application.AgentPlannerClient.ConversationMessage;
import com.adproject.agent.application.AgentPlannerClient.PlanOperation;
import com.adproject.agent.application.AgentPlannerClient.PlanResponse;
import com.adproject.agent.application.AgentPlannerClient.PlannerException;
import com.adproject.agent.domain.AgentConfirmationStatus;
import com.adproject.agent.domain.AgentRunStatus;
import com.adproject.agent.domain.AgentStepStatus;
import com.adproject.agent.domain.AgentStepType;
import com.adproject.agent.infrastructure.AgentRunEntity;
import com.adproject.agent.infrastructure.AgentRunRepository;
import com.adproject.agent.infrastructure.AgentStepEntity;
import com.adproject.agent.infrastructure.AgentStepRepository;
import com.adproject.application.api.InterviewDtos;
import com.adproject.application.application.InterviewService;
import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.domain.InterviewMode;
import com.adproject.application.domain.InterviewStatus;
import com.adproject.application.domain.MeetingProvider;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.application.infrastructure.InterviewEntity;
import com.adproject.application.infrastructure.InterviewRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recruiter-side agent runs. The planner (agent-service) never sees business data; screening is
 * executed here by reading the candidate pool and calling DeepSeek directly, and interview tools
 * reuse {@link InterviewService} through the same preview-then-confirm flow as resume edits.
 */
@Service
public class HrAgentRunService implements AgentRunsPort {
    private static final String SCREEN = "screen_applicants";
    private static final String SCHEDULE = "schedule_interview";
    private static final String RESCHEDULE = "reschedule_interview";
    private static final String CANCEL = "cancel_interview";
    private static final int POOL_CAP = 30;
    private static final TypeReference<List<Object>> OBJECT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final AgentPlannerClient planner;
    private final HrScreeningClient screening;
    private final AgentProperties properties;
    private final InterviewService interviewService;
    private final InterviewRepository interviews;
    private final ApplicationRepository applications;
    private final ResumeRepository resumes;
    private final JobRepository jobs;
    private final CompanyMemberRepository members;
    private final ObjectMapper mapper;
    private final Clock clock;

    public HrAgentRunService(AgentRunRepository runs, AgentStepRepository steps,
                             AgentPlannerClient planner, HrScreeningClient screening,
                             AgentProperties properties, InterviewService interviewService,
                             InterviewRepository interviews, ApplicationRepository applications,
                             ResumeRepository resumes, JobRepository jobs,
                             CompanyMemberRepository members, ObjectMapper mapper, Clock clock) {
        this.runs = runs;
        this.steps = steps;
        this.planner = planner;
        this.screening = screening;
        this.properties = properties;
        this.interviewService = interviewService;
        this.interviews = interviews;
        this.applications = applications;
        this.resumes = resumes;
        this.jobs = jobs;
        this.members = members;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public AgentDtos.RunResponse create(AuthenticatedUser principal, AgentDtos.CreateRunRequest request) {
        String companyId = requireCompany(principal);
        Instant now = clock.instant();
        String jobId = blankToNull(request.jobId());
        if (jobId != null) {
            jobId = requireUuid(jobId, "jobId");
        }
        String timezone = blankToNull(request.timezone());
        String conversationId = conversationId(principal, request.conversationId());
        List<ConversationMessage> history = plannerHistory(principal.userId(), conversationId);
        AgentRunEntity run = runs.save(new AgentRunEntity(UUID.randomUUID().toString(), principal.userId(),
                conversationId, request.instruction().trim(), now));
        run.setJobId(jobId);

        PlanResponse plan;
        long planStarted = System.nanoTime();
        try {
            plan = planner.plan(run.getInstruction(), "RECRUITER", jobId,
                    clock.instant().toString(), timezone, history);
            addStep(run, 1, AgentStepType.PLAN, null, "instruction received",
                    summarizePlan(plan), AgentStepStatus.SUCCEEDED, null, elapsedMillis(planStarted));
        } catch (PlannerException exception) {
            addStep(run, 1, AgentStepType.PLAN, null, "instruction received", null,
                    AgentStepStatus.FAILED, exception.getCode(), elapsedMillis(planStarted));
            if ("AGENT_PLAN_REJECTED".equals(exception.getCode())) {
                run.needsClarification("Please provide a supported recruiter instruction: screen candidates "
                        + "for one of your jobs, or schedule, reschedule, or cancel an interview.", now);
            } else {
                run.fail(exception.getCode(), "The agent could not prepare a plan. Please try again.", now);
            }
            return response(run);
        }

        if ("CHAT".equals(plan.status())) {
            if (!"CHAT".equals(plan.intent()) || plan.target() != null
                    || plan.operations() == null || !plan.operations().isEmpty()) {
                run.fail("AGENT_PLAN_REJECTED", "The generated chat response is not allowed.", now);
                addStep(run, 2, AgentStepType.SYSTEM, null, "validate chat response", null,
                        AgentStepStatus.FAILED, "AGENT_PLAN_REJECTED", 0);
                return response(run);
            }
            run.completeChat(safeMessage(plan.message(),
                    "How can I help with your recruiting work?"), now);
            return response(run);
        }

        if (!"READY".equals(plan.status())) {
            run.needsClarification(safeMessage(plan.message(),
                    "Please provide a supported recruiter instruction."), now);
            return response(run);
        }

        String tool = switch (plan.intent() == null ? "" : plan.intent()) {
            case "SCREEN_APPLICANTS" -> SCREEN;
            case "SCHEDULE_INTERVIEW" -> SCHEDULE;
            case "RESCHEDULE_INTERVIEW" -> RESCHEDULE;
            case "CANCEL_INTERVIEW" -> CANCEL;
            default -> null;
        };
        if (tool == null || plan.target() != null || !hasExactTools(plan, Set.of(tool))) {
            run.fail("AGENT_PLAN_REJECTED", "The generated plan is not allowed.", now);
            addStep(run, 2, AgentStepType.SYSTEM, null, "validate tool whitelist", null,
                    AgentStepStatus.FAILED, "AGENT_PLAN_REJECTED", 0);
            return response(run);
        }
        PlanOperation operation = operation(plan, tool);
        if (SCREEN.equals(tool)) {
            screenCandidates(run, operation, companyId, now);
        } else {
            prepareInterviewPreview(run, tool, operation, companyId, plan.message(), now);
        }
        return response(run);
    }

    @Transactional
    public ConfirmResult confirm(AuthenticatedUser principal, String runId, String rawIdempotencyKey,
                                 AgentDtos.ConfirmRunRequest request) {
        requireRecruiter(principal);
        String idempotencyKey = requireUuid(rawIdempotencyKey, "Idempotency-Key");
        AgentRunEntity run = ownedRunForUpdate(runId, principal.userId());

        if (run.getStatus() == AgentRunStatus.COMPLETED) {
            if (idempotencyKey.equals(run.getExecutionIdempotencyKey())
                    && request.confirmationId().equals(run.getConfirmationId())) {
                return new ConfirmResult(HttpStatus.OK, response(run));
            }
            throw conflict("AGENT_CONFIRMATION_ALREADY_USED", "The agent run was already confirmed");
        }
        if (run.getStatus() != AgentRunStatus.AWAITING_CONFIRMATION
                || run.getConfirmationStatus() != AgentConfirmationStatus.PENDING) {
            throw conflict("AGENT_RUN_NOT_CONFIRMABLE", "The agent run cannot be confirmed in its current state");
        }
        if (run.getVersion() != request.expectedRunVersion()) {
            throw conflict("AGENT_RUN_VERSION_CONFLICT", "The agent run changed after it was displayed");
        }
        if (!request.confirmationId().equals(run.getConfirmationId())) {
            throw conflict("AGENT_CONFIRMATION_MISMATCH", "The confirmation does not match this preview");
        }
        Instant now = clock.instant();
        int sequence = nextSequence(runId);
        if (run.getPreviewExpiresAt() == null || !now.isBefore(run.getPreviewExpiresAt())) {
            run.expire("The change preview expired. Create a new agent run.", now);
            addStep(run, sequence, AgentStepType.SYSTEM, null, "confirmation expired", null,
                    AgentStepStatus.FAILED, "AGENT_CONFIRMATION_EXPIRED", 0);
            return new ConfirmResult(HttpStatus.CONFLICT, response(run));
        }
        var reusedKey = runs.findByUserIdAndExecutionIdempotencyKey(principal.userId(), idempotencyKey);
        if (reusedKey.isPresent() && !reusedKey.get().getId().equals(runId)) {
            throw conflict("IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for another agent run");
        }

        AgentDtos.Preview preview = readPreview(run.getPreviewJson());
        if (preview == null || !Objects.equals(run.getTargetId(), preview.targetId())
                || !Set.of("APPLICATION", "INTERVIEW").contains(preview.targetType())) {
            return rejectInvalidPreview(run, sequence, now);
        }
        InterviewExecution execution;
        try {
            execution = parsePreview(preview);
        } catch (RuntimeException exception) {
            return rejectInvalidPreview(run, sequence, now);
        }

        run.startExecution(idempotencyKey, now);
        addStep(run, sequence++, AgentStepType.SYSTEM, null,
                "confirmationId=" + request.confirmationId() + "; expectedRunVersion=" + request.expectedRunVersion(),
                "confirmation accepted", AgentStepStatus.SUCCEEDED, null, 0);
        return executeInterview(run, preview, execution, principal, sequence, now);
    }

    @Transactional(readOnly = true)
    public AgentDtos.RunResponse get(AuthenticatedUser principal, String runId) {
        requireRecruiter(principal);
        AgentRunEntity run = ownedRun(runId, principal.userId());
        return response(run);
    }

    @Transactional(readOnly = true)
    public AgentDtos.ConversationResponse recentConversation(AuthenticatedUser principal) {
        requireRecruiter(principal);
        return runs.findFirstByUserIdOrderByUpdatedAtDesc(principal.userId())
                .map(run -> conversationResponse(principal.userId(), run.getConversationId()))
                .orElseGet(() -> new AgentDtos.ConversationResponse(
                        new AgentDtos.Conversation(null, List.of())));
    }

    @Transactional(readOnly = true)
    public AgentDtos.ConversationListResponse listConversations(AuthenticatedUser principal) {
        requireRecruiter(principal);
        List<AgentDtos.ConversationSummary> summaries = runs.findLatestRunPerConversation(
                        principal.userId(), PageRequest.of(0, 30))
                .stream()
                .map(run -> new AgentDtos.ConversationSummary(
                        run.getConversationId(), run.getInstruction(), run.getMessage(), run.getUpdatedAt()))
                .toList();
        return new AgentDtos.ConversationListResponse(summaries);
    }

    @Transactional(readOnly = true)
    public AgentDtos.ConversationResponse getConversation(AuthenticatedUser principal, String rawConversationId) {
        requireRecruiter(principal);
        String conversationId = requireUuid(rawConversationId, "conversationId");
        if (!runs.existsByConversationIdAndUserId(conversationId, principal.userId())) throw notFound();
        return conversationResponse(principal.userId(), conversationId);
    }

    @Transactional
    public AgentDtos.RunResponse cancel(AuthenticatedUser principal, String runId) {
        requireRecruiter(principal);
        AgentRunEntity run = ownedRunForUpdate(runId, principal.userId());
        if (run.getStatus() == AgentRunStatus.CANCELLED) {
            return response(run);
        }
        if (run.getStatus() != AgentRunStatus.AWAITING_CONFIRMATION
                && run.getStatus() != AgentRunStatus.NEEDS_CLARIFICATION) {
            throw new ApiException(HttpStatus.CONFLICT, "AGENT_RUN_NOT_CANCELLABLE",
                    "The agent run cannot be cancelled in its current state");
        }
        int sequence = steps.findByRunIdOrderBySequenceNoAsc(runId).size() + 1;
        run.cancel(clock.instant());
        addStep(run, sequence, AgentStepType.SYSTEM, null, "cancel requested",
                "run cancelled", AgentStepStatus.SUCCEEDED, null, 0);
        return response(run);
    }

    // ------------------------------------------------------------------
    // Screening
    // ------------------------------------------------------------------

    private void screenCandidates(AgentRunEntity run, PlanOperation operation, String companyId, Instant now) {
        JobEntity job;
        try {
            job = resolveJob(run, operation, companyId);
        } catch (PlanClarification exception) {
            run.needsClarification(exception.getMessage(), now);
            addStep(run, 2, AgentStepType.SYSTEM, SCREEN, "validate job reference", null,
                    AgentStepStatus.FAILED, "AGENT_ARGUMENT_INVALID", 0);
            return;
        }
        HrScreeningClient.ScreeningInput input = screeningInput(job);
        long started = System.nanoTime();
        try {
            HrScreeningClient.ScreeningOutput output = screening.rank(input);
            addStep(run, 2, AgentStepType.TOOL, SCREEN,
                    "jobId=" + job.getId() + "; candidates=" + input.candidates().size(),
                    "ranked=" + output.ranked().size(), AgentStepStatus.SUCCEEDED, null,
                    elapsedMillis(started));
            AgentDtos.ScreeningResult result = screeningResult(job, input, output);
            run.completeRead("JOB", job.getId(), writeJson(result), result.message(), clock.instant());
        } catch (HrScreeningClient.ScreeningException exception) {
            addStep(run, 2, AgentStepType.TOOL, SCREEN, "jobId=" + job.getId(), null,
                    AgentStepStatus.FAILED, exception.getCode(), elapsedMillis(started));
            run.fail(exception.getCode(), "The resume screening service failed. Please try again.", clock.instant());
        }
    }

    private JobEntity resolveJob(AgentRunEntity run, PlanOperation operation, String companyId) {
        Map<String, Object> arguments = operation == null ? Map.of() : operation.arguments();
        String jobId = blankToNull(stringArgument(arguments, "jobId"));
        if (jobId == null) {
            jobId = run.getJobId();
        }
        if (jobId != null) {
            try {
                jobId = requireUuid(jobId, "jobId");
            } catch (ApiException exception) {
                throw new PlanClarification("The job reference is invalid. Name the job by title instead.");
            }
            JobEntity job = jobs.findById(jobId).orElse(null);
            if (job == null || !companyId.equals(job.getCompanyId())) {
                throw new PlanClarification("The requested job does not exist or does not belong to your "
                        + "company. Name one of your jobs by title instead.");
            }
            return job;
        }
        String selector = blankToNull(stringArgument(arguments, "jobSelector"));
        if (selector == null) {
            throw new PlanClarification("Which job should be screened? Name the job title.");
        }
        List<JobEntity> matches = jobs.findByCompanyId(companyId, PageRequest.of(0, 100)).stream()
                .filter(job -> job.getTitle() != null
                        && job.getTitle().toLowerCase().contains(selector.toLowerCase()))
                .toList();
        if (matches.isEmpty()) {
            throw new PlanClarification("No job of yours matched \"" + selector + "\". Use an exact job title.");
        }
        if (matches.size() > 1) {
            String titles = matches.stream().map(JobEntity::getTitle).distinct().limit(5)
                    .collect(Collectors.joining(", "));
            throw new PlanClarification("Multiple jobs matched \"" + selector + "\": " + titles
                    + ". Name the exact job title.");
        }
        return matches.getFirst();
    }

    private HrScreeningClient.ScreeningInput screeningInput(JobEntity job) {
        List<ResumeEntity> pool = resumes.findAll(PageRequest.of(0, POOL_CAP,
                Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent();
        List<HrScreeningClient.Candidate> candidates = new ArrayList<>();
        for (ResumeEntity resume : pool) {
            ApplicationEntity application = applications
                    .findByJobIdAndCandidateId(job.getId(), resume.getCandidateId()).orElse(null);
            candidates.add(new HrScreeningClient.Candidate(
                    resume.getCandidateId(),
                    application == null ? null : application.getId(),
                    resume.getFullName(),
                    resume.getHeadline(),
                    resume.getLocation(),
                    resume.getSummary(),
                    jsonStringList(resume.getSkillsJson()),
                    jsonList(resume.getExperiencesJson()),
                    application == null ? null : application.getStatus().name()));
        }
        return new HrScreeningClient.ScreeningInput(job.getId(), job.getTitle(), job.getLocation(),
                job.getDescription(), job.getEmploymentType().name(), job.getWorkplaceType().name(), candidates);
    }

    private AgentDtos.ScreeningResult screeningResult(JobEntity job, HrScreeningClient.ScreeningInput input,
                                                      HrScreeningClient.ScreeningOutput output) {
        Map<String, HrScreeningClient.Candidate> byId = input.candidates().stream()
                .collect(Collectors.toMap(HrScreeningClient.Candidate::candidateId, candidate -> candidate));
        List<AgentDtos.RankedCandidate> ranked = output.ranked().stream().map(ranking -> {
            HrScreeningClient.Candidate candidate = byId.get(ranking.candidateId());
            return new AgentDtos.RankedCandidate(candidate.candidateId(), candidate.applicationId(),
                    candidate.fullName(), candidate.applicationStatus(), ranking.rank(),
                    ranking.strongMatches(), ranking.gaps());
        }).toList();
        return new AgentDtos.ScreeningResult(job.getId(), job.getTitle(), ranked,
                shortlistMessage(output.message(), ranked));
    }

    /**
     * The run message is what later turns of the conversation see, so it must carry the shortlist
     * with application ids; that is how follow-ups like "schedule the top candidate" resolve.
     */
    private String shortlistMessage(String modelMessage, List<AgentDtos.RankedCandidate> ranked) {
        String base = modelMessage == null || modelMessage.isBlank() ? "" : modelMessage.trim();
        List<String> entries = new ArrayList<>();
        for (AgentDtos.RankedCandidate candidate : ranked.stream().limit(5).toList()) {
            StringBuilder entry = new StringBuilder(candidate.rank() + ". " + candidate.fullName() + "(");
            if (candidate.applicationStatus() == null) {
                entry.append("未投递");
            } else {
                entry.append(candidate.applicationStatus()).append(", applicationId=").append(candidate.applicationId());
            }
            entries.add(entry.append(")").toString());
        }
        String summary = " 筛选结果: " + String.join("; ", entries);
        int available = Math.max(0, 500 - summary.length() - 1);
        if (available == 0) {
            return summary.length() <= 500 ? summary : summary.substring(0, 500);
        }
        if (base.length() > available) {
            base = base.substring(0, available).trim();
        }
        return base.isEmpty() ? summary : base + "\n" + summary;
    }

    private List<Object> jsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, OBJECT_LIST);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<String> jsonStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, STRING_LIST);
        } catch (Exception exception) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // Interview previews
    // ------------------------------------------------------------------

    private void prepareInterviewPreview(AgentRunEntity run, String tool, PlanOperation operation,
                                         String companyId, String planMessage, Instant now) {
        try {
            Map<String, Object> arguments = operation == null ? Map.of() : operation.arguments();
            String applicationId = requireAgentUuid(stringArgument(arguments, "applicationId"));
            ApplicationEntity application = applications.findById(applicationId).orElseThrow(
                    () -> new PlanClarification("The referenced application does not exist. "
                            + "Use a candidate from a screening result."));
            JobEntity job = jobs.findById(application.getJobId()).orElseThrow(
                    () -> new PlanClarification("The application's job no longer exists."));
            if (!companyId.equals(job.getCompanyId())) {
                throw new PlanClarification("This application does not belong to your company.");
            }
            AgentDtos.Preview preview;
            if (SCHEDULE.equals(tool)) {
                preview = schedulePreview(application, arguments, now);
            } else {
                InterviewEntity interview = interviews.findByApplicationId(applicationId).orElseThrow(
                        () -> new PlanClarification("This candidate has no scheduled interview. "
                                + "Ask me to schedule one instead."));
                preview = RESCHEDULE.equals(tool)
                        ? reschedulePreview(interview, arguments, now)
                        : cancelPreview(interview, now);
            }
            addStep(run, 2, AgentStepType.TOOL, tool, "applicationId=" + applicationId,
                    "targetId=" + preview.targetId() + "; expectedVersion=" + preview.expectedVersion(),
                    AgentStepStatus.SUCCEEDED, null, 0);
            run.awaitingConfirmation(preview.targetType(), preview.targetId(), writeJson(preview),
                    preview.confirmationId(), preview.expiresAt(),
                    safeMessage(planMessage, "Review the proposed interview change."), now);
        } catch (PlanClarification exception) {
            run.needsClarification(exception.getMessage(), now);
            addStep(run, 2, AgentStepType.SYSTEM, tool, "validate interview request", null,
                    AgentStepStatus.FAILED, "AGENT_ARGUMENT_INVALID", 0);
        }
    }

    private AgentDtos.Preview schedulePreview(ApplicationEntity application, Map<String, Object> arguments,
                                              Instant now) {
        if (application.getStatus() != ApplicationStatus.IN_REVIEW) {
            throw new PlanClarification("An interview can only be scheduled while the application is in "
                    + "review; the current status is " + application.getStatus() + ".");
        }
        if (interviews.existsByApplicationId(application.getId())) {
            throw new PlanClarification("This candidate already has a scheduled interview. "
                    + "Ask me to reschedule or cancel it if needed.");
        }
        Instant scheduledAt = instantArgument(arguments, "scheduledAt");
        String timezone = requiredStringArgument(arguments, "timezone", "Please provide the interview timezone.");
        int durationMinutes = integerArgument(arguments, "durationMinutes", 60);
        InterviewMode mode = modeArgument(arguments);
        List<AgentDtos.FieldChange> changes = List.of(
                new AgentDtos.FieldChange("scheduledAt", null, scheduledAt.toString()),
                new AgentDtos.FieldChange("timezone", null, timezone),
                new AgentDtos.FieldChange("durationMinutes", null, durationMinutes),
                new AgentDtos.FieldChange("mode", null, mode.name()));
        return newPreview("APPLICATION", application.getId(), application.getVersion(), changes, now);
    }

    private AgentDtos.Preview reschedulePreview(InterviewEntity interview, Map<String, Object> arguments,
                                                Instant now) {
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw new PlanClarification("This interview is " + interview.getStatus().name().toLowerCase()
                    + " and cannot be rescheduled.");
        }
        Instant scheduledAt = instantArgument(arguments, "scheduledAt");
        String timezone = requiredStringArgument(arguments, "timezone", "Please provide the interview timezone.");
        List<AgentDtos.FieldChange> changes = new ArrayList<>(List.of(
                new AgentDtos.FieldChange("scheduledAt", interview.getScheduledAt().toString(), scheduledAt.toString()),
                new AgentDtos.FieldChange("timezone", interview.getTimezone(), timezone)));
        if (arguments.containsKey("durationMinutes")) {
            changes.add(new AgentDtos.FieldChange("durationMinutes", interview.getDurationMinutes(),
                    integerArgument(arguments, "durationMinutes", interview.getDurationMinutes())));
        }
        if (arguments.containsKey("mode")) {
            InterviewMode mode = modeArgument(arguments);
            if (interview.getMeetingProvider() == MeetingProvider.GOOGLE_MEET && mode != InterviewMode.ONLINE) {
                throw new PlanClarification("Online interviews always use Google Meet, so the mode cannot be changed.");
            }
            changes.add(new AgentDtos.FieldChange("mode", interview.getMode().name(), mode.name()));
        }
        return newPreview("INTERVIEW", interview.getId(), interview.getVersion(), changes, now);
    }

    private AgentDtos.Preview cancelPreview(InterviewEntity interview, Instant now) {
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw new PlanClarification("This interview is " + interview.getStatus().name().toLowerCase()
                    + " and cannot be cancelled.");
        }
        return newPreview("INTERVIEW", interview.getId(), interview.getVersion(),
                List.of(new AgentDtos.FieldChange("status", interview.getStatus().name(), "CANCELLED")), now);
    }

    private AgentDtos.Preview newPreview(String targetType, String targetId, int expectedVersion,
                                         List<AgentDtos.FieldChange> changes, Instant now) {
        return new AgentDtos.Preview(UUID.randomUUID().toString(), targetType, targetId, expectedVersion,
                now.plusSeconds(properties.previewTtlSeconds()), changes);
    }

    // ------------------------------------------------------------------
    // Interview execution
    // ------------------------------------------------------------------

    private record InterviewExecution(String tool, InterviewDtos.CreateInterviewRequest create,
                                      InterviewDtos.UpdateInterviewRequest update) {}

    private InterviewExecution parsePreview(AgentDtos.Preview preview) {
        if ("APPLICATION".equals(preview.targetType())) {
            return new InterviewExecution(SCHEDULE, createRequestFromPreview(preview), null);
        }
        if ("INTERVIEW".equals(preview.targetType())) {
            InterviewDtos.UpdateInterviewRequest update = updateRequestFromPreview(preview);
            String tool = update.status() == InterviewStatus.CANCELLED ? CANCEL : RESCHEDULE;
            return new InterviewExecution(tool, null, update);
        }
        throw new IllegalArgumentException("unsupported preview target");
    }

    private ConfirmResult executeInterview(AgentRunEntity run, AgentDtos.Preview preview,
                                           InterviewExecution execution, AuthenticatedUser principal,
                                           int sequence, Instant now) {
        String requestId = "agent:" + run.getId();
        try {
            if (execution.create() != null) {
                InterviewDtos.Interview saved = interviewService.create(
                        principal, preview.targetId(), execution.create(), requestId);
                addStep(run, sequence, AgentStepType.TOOL, execution.tool(),
                        "applicationId=" + preview.targetId(), "interviewId=" + saved.interviewId(),
                        AgentStepStatus.SUCCEEDED, null, 0);
                AgentDtos.ExecutionResult result = new AgentDtos.ExecutionResult("SCHEDULE_INTERVIEW",
                        "APPLICATION", preview.targetId(), preview.expectedVersion(), saved.version(),
                        clock.instant(), preview.changes(), null);
                run.complete(writeJson(result), "The interview was scheduled successfully.", clock.instant());
            } else {
                InterviewDtos.Interview saved = interviewService.update(
                        principal, preview.targetId(), execution.update(), requestId);
                String operation = CANCEL.equals(execution.tool()) ? "CANCEL_INTERVIEW" : "RESCHEDULE_INTERVIEW";
                String message = CANCEL.equals(execution.tool())
                        ? "The interview was cancelled successfully."
                        : "The interview was rescheduled successfully.";
                addStep(run, sequence, AgentStepType.TOOL, execution.tool(),
                        "interviewId=" + preview.targetId(), "interviewId=" + saved.interviewId(),
                        AgentStepStatus.SUCCEEDED, null, 0);
                AgentDtos.ExecutionResult result = new AgentDtos.ExecutionResult(operation,
                        "INTERVIEW", preview.targetId(), preview.expectedVersion(), saved.version(),
                        clock.instant(), preview.changes(), null);
                run.complete(writeJson(result), message, clock.instant());
            }
            return new ConfirmResult(HttpStatus.OK, response(run));
        } catch (ApiException exception) {
            run.rejectExecution(exception.getCode(),
                    safeMessage(exception.getMessage(), "The interview change could not be applied."), now);
            addStep(run, sequence, AgentStepType.TOOL, execution.tool(),
                    "targetId=" + preview.targetId(), null, AgentStepStatus.FAILED, exception.getCode(), 0);
            return new ConfirmResult(HttpStatus.CONFLICT, response(run));
        } catch (RuntimeException exception) {
            run.rejectExecution("AGENT_PREVIEW_INVALID", "The stored interview preview is invalid.", now);
            addStep(run, sequence, AgentStepType.TOOL, execution.tool(),
                    "targetId=" + preview.targetId(), null, AgentStepStatus.FAILED, "AGENT_PREVIEW_INVALID", 0);
            return new ConfirmResult(HttpStatus.CONFLICT, response(run));
        }
    }

    private InterviewDtos.CreateInterviewRequest createRequestFromPreview(AgentDtos.Preview preview) {
        Map<String, AgentDtos.FieldChange> byField = changesByField(preview.changes());
        InterviewMode mode = InterviewMode.valueOf(requiredNewString(byField, "mode"));
        MeetingProvider provider = mode == InterviewMode.ONLINE
                ? MeetingProvider.GOOGLE_MEET : MeetingProvider.MANUAL;
        return new InterviewDtos.CreateInterviewRequest(
                Instant.parse(requiredNewString(byField, "scheduledAt")),
                requiredNewString(byField, "timezone"),
                requiredNewInteger(byField, "durationMinutes"),
                mode, null, null, provider, preview.expectedVersion());
    }

    private InterviewDtos.UpdateInterviewRequest updateRequestFromPreview(AgentDtos.Preview preview) {
        Map<String, AgentDtos.FieldChange> byField = changesByField(preview.changes());
        AgentDtos.FieldChange status = byField.get("status");
        if (status != null && "CANCELLED".equals(stringValue(status.newValue()))) {
            return new InterviewDtos.UpdateInterviewRequest(null, null, null, null, null, null,
                    InterviewStatus.CANCELLED, preview.expectedVersion());
        }
        return new InterviewDtos.UpdateInterviewRequest(
                Instant.parse(requiredNewString(byField, "scheduledAt")),
                requiredNewString(byField, "timezone"),
                byField.containsKey("durationMinutes")
                        ? integerValue(byField.get("durationMinutes").newValue()) : null,
                byField.containsKey("mode")
                        ? InterviewMode.valueOf(requiredNewString(byField, "mode")) : null,
                null, null, null, preview.expectedVersion());
    }

    private static Map<String, AgentDtos.FieldChange> changesByField(List<AgentDtos.FieldChange> changes) {
        Map<String, AgentDtos.FieldChange> byField = new LinkedHashMap<>();
        if (changes != null) {
            for (AgentDtos.FieldChange change : changes) {
                if (change != null && change.field() != null) {
                    byField.put(change.field(), change);
                }
            }
        }
        return byField;
    }

    private static String requiredNewString(Map<String, AgentDtos.FieldChange> byField, String field) {
        AgentDtos.FieldChange change = byField.get(field);
        String value = change == null ? null : stringValue(change.newValue());
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing interview field " + field);
        }
        return value;
    }

    private static Integer requiredNewInteger(Map<String, AgentDtos.FieldChange> byField, String field) {
        AgentDtos.FieldChange change = byField.get(field);
        Integer value = change == null ? null : integerValue(change.newValue());
        if (value == null || value < 1 || value > 1440) {
            throw new IllegalArgumentException("missing interview field " + field);
        }
        return value;
    }

    private ConfirmResult rejectInvalidPreview(AgentRunEntity run, int sequence, Instant now) {
        run.rejectExecution("AGENT_PREVIEW_INVALID", "The stored interview preview is invalid.", now);
        addStep(run, sequence, AgentStepType.SYSTEM, null, "validate stored preview", null,
                AgentStepStatus.FAILED, "AGENT_PREVIEW_INVALID", 0);
        return new ConfirmResult(HttpStatus.CONFLICT, response(run));
    }

    // ------------------------------------------------------------------
    // Responses and history
    // ------------------------------------------------------------------

    private AgentDtos.RunResponse response(AgentRunEntity run) {
        AgentDtos.Target target = run.getTargetId() == null ? null
                : new AgentDtos.Target(run.getTargetType(), run.getTargetId());
        List<AgentDtos.Step> runSteps = steps.findByRunIdOrderBySequenceNoAsc(run.getId()).stream()
                .map(step -> new AgentDtos.Step(step.getSequenceNo(), step.getStepType().name(), step.getToolName(),
                        step.getStatus().name(), step.getInputSummary(), step.getOutputSummary(), step.getErrorCode(),
                        step.getDurationMs(), step.getCreatedAt()))
                .toList();
        AgentDtos.Preview preview = readPreview(run.getPreviewJson());
        boolean screeningRun = "JOB".equals(run.getTargetType());
        AgentDtos.ExecutionResult result = screeningRun ? null : readResult(run.getResultJson());
        AgentDtos.ScreeningResult screenResult = screeningRun ? readScreening(run.getResultJson()) : null;
        return new AgentDtos.RunResponse(new AgentDtos.Run(run.getId(), run.getConversationId(),
                run.getInstruction(), run.getStatus().name(),
                run.getConfirmationStatus().name(), target, runSteps, preview, result, screenResult,
                run.getMessage(), run.getErrorCode(),
                run.getVersion(), run.getCreatedAt(), run.getUpdatedAt()));
    }

    private AgentDtos.ConversationResponse conversationResponse(String userId, String conversationId) {
        List<AgentRunEntity> entities = conversationRuns(userId, conversationId, 50);
        List<AgentDtos.Run> history = entities.stream().map(entity -> response(entity).data()).toList();
        return new AgentDtos.ConversationResponse(new AgentDtos.Conversation(conversationId, history));
    }

    private String conversationId(AuthenticatedUser principal, String requested) {
        if (requested == null || requested.isBlank()) return UUID.randomUUID().toString();
        String conversationId = requireUuid(requested, "conversationId");
        if (!runs.existsByConversationIdAndUserId(conversationId, principal.userId())) throw notFound();
        return conversationId;
    }

    private List<ConversationMessage> plannerHistory(String userId, String conversationId) {
        List<ConversationMessage> messages = new ArrayList<>();
        for (AgentRunEntity prior : conversationRuns(userId, conversationId, 10)) {
            messages.add(new ConversationMessage("user", prior.getInstruction()));
            if (prior.getMessage() != null && !prior.getMessage().isBlank()) {
                messages.add(new ConversationMessage("assistant", prior.getMessage()));
            }
        }
        return messages.size() <= 20 ? messages : messages.subList(messages.size() - 20, messages.size());
    }

    private List<AgentRunEntity> conversationRuns(String userId, String conversationId, int limit) {
        List<AgentRunEntity> result = new ArrayList<>(runs.findByConversationIdAndUserId(
                conversationId, userId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
        Collections.reverse(result);
        return result;
    }

    private void addStep(AgentRunEntity run, int sequence, AgentStepType type, String tool,
                         String input, String output, AgentStepStatus status, String errorCode, long durationMs) {
        steps.save(new AgentStepEntity(UUID.randomUUID().toString(), run.getId(), sequence, type, tool,
                input, output, status, errorCode, durationMs, clock.instant()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PlanOperation operation(PlanResponse response, String tool) {
        if (response.operations() == null) return null;
        return response.operations().stream()
                .filter(value -> value != null && tool.equals(value.tool()))
                .findFirst().orElse(null);
    }

    private boolean hasExactTools(PlanResponse response, Set<String> expected) {
        if (expected.isEmpty() || response.operations() == null || response.operations().size() != expected.size()) {
            return false;
        }
        Set<String> actual = new LinkedHashSet<>();
        for (PlanOperation operation : response.operations()) {
            if (operation == null || operation.tool() == null) return false;
            actual.add(operation.tool());
        }
        return actual.equals(expected);
    }

    private String summarizePlan(PlanResponse plan) {
        String summary = "status=" + safe(plan.status()) + "; intent=" + safe(plan.intent())
                + "; tools=" + (plan.operations() == null ? List.of() : plan.operations().stream().filter(Objects::nonNull)
                .map(PlanOperation::tool).toList());
        return safeMessage(summary, "plan received");
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize agent data", exception);
        }
    }

    private AgentDtos.Preview readPreview(String value) {
        if (value == null) return null;
        try {
            return mapper.readValue(value, AgentDtos.Preview.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored agent preview is invalid", exception);
        }
    }

    private AgentDtos.ExecutionResult readResult(String value) {
        if (value == null) return null;
        try {
            return mapper.readValue(value, AgentDtos.ExecutionResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored agent result is invalid", exception);
        }
    }

    private AgentDtos.ScreeningResult readScreening(String value) {
        if (value == null) return null;
        try {
            return mapper.readValue(value, AgentDtos.ScreeningResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored agent screening result is invalid", exception);
        }
    }

    private int nextSequence(String runId) {
        return steps.findByRunIdOrderBySequenceNoAsc(runId).size() + 1;
    }

    private Instant instantArgument(Map<String, Object> arguments, String key) {
        String value = blankToNull(stringArgument(arguments, key));
        if (value == null) {
            throw new PlanClarification("Please provide the interview time.");
        }
        try {
            return Instant.parse(value);
        } catch (Exception exception) {
            throw new PlanClarification("The interview time is invalid.");
        }
    }

    private InterviewMode modeArgument(Map<String, Object> arguments) {
        String mode = blankToNull(stringArgument(arguments, "mode"));
        if (mode == null) {
            return InterviewMode.ONLINE;
        }
        try {
            return InterviewMode.valueOf(mode);
        } catch (IllegalArgumentException exception) {
            throw new PlanClarification("The interview mode must be ONLINE, ONSITE, or PHONE.");
        }
    }

    private String requiredStringArgument(Map<String, Object> arguments, String key, String clarification) {
        String value = blankToNull(stringArgument(arguments, key));
        if (value == null) {
            throw new PlanClarification(clarification);
        }
        return value;
    }

    private static Integer integerArgument(Map<String, Object> arguments, String key, int fallback) {
        Object raw = arguments.get(key);
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    private String requireAgentUuid(String value) {
        try {
            return requireUuid(value, "applicationId");
        } catch (ApiException exception) {
            throw new PlanClarification("The application reference is invalid. "
                    + "Use a candidate from a screening result.");
        }
    }

    private static String stringArgument(Map<String, Object> arguments, String key) {
        return arguments == null ? null : stringValue(arguments.get(key));
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", Map.of(field, "is required"));
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", Map.of(field, "must be a UUID"));
        }
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private static String safeMessage(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String safe(String value) {
        return value == null ? "null" : value;
    }

    private static void requireRecruiter(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private String requireCompany(AuthenticatedUser principal) {
        requireRecruiter(principal);
        return members.findByUserId(principal.userId()).map(CompanyMemberEntity::getCompanyId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Agent run not found");
    }

    /** Locked ownership lookup: a run owned by another user is 403, a missing run is 404. */
    private AgentRunEntity ownedRunForUpdate(String runId, String userId) {
        return runs.findOwnedForUpdate(runId, userId).orElseGet(() -> {
            if (runs.existsById(runId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
            }
            throw notFound();
        });
    }

    private AgentRunEntity ownedRun(String runId, String userId) {
        return runs.findByIdAndUserId(runId, userId).orElseGet(() -> {
            if (runs.existsById(runId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
            }
            throw notFound();
        });
    }

    private static final class PlanClarification extends RuntimeException {
        private PlanClarification(String message) {
            super(message);
        }
    }
}
