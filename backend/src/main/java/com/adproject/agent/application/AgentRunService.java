package com.adproject.agent.application;

import com.adproject.agent.api.AgentDtos;
import com.adproject.agent.application.AgentPlannerClient.PlanOperation;
import com.adproject.agent.application.AgentPlannerClient.PlanResponse;
import com.adproject.agent.application.AgentPlannerClient.PlannerException;
import com.adproject.agent.application.AgentPlannerClient.ConversationMessage;
import com.adproject.agent.domain.AgentRunStatus;
import com.adproject.agent.domain.AgentStepStatus;
import com.adproject.agent.domain.AgentStepType;
import com.adproject.agent.infrastructure.AgentRunEntity;
import com.adproject.agent.infrastructure.AgentRunRepository;
import com.adproject.agent.infrastructure.AgentStepEntity;
import com.adproject.agent.infrastructure.AgentStepRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.resume.application.CandidateResumeService;
import com.adproject.resume.api.ResumeDtos.Experience;
import com.adproject.resume.api.ResumeDtos.Resume;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunService implements AgentRunsPort {
    private static final String GET_RESUME = "get_my_resume";
    private static final String READ_SECTION = "read_resume_section";
    private static final String PREVIEW_PATCH = "preview_resume_patch";
    private static final String APPLY_PATCH = "apply_resume_patch";
    private static final Set<String> QUERY_TOOLS = Set.of(GET_RESUME, READ_SECTION);
    private static final Set<String> UPDATE_TOOLS = Set.of(GET_RESUME, PREVIEW_PATCH);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Experience>> EXPERIENCE_LIST = new TypeReference<>() {};

    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final CandidateResumeService resumeService;
    private final ResumeRepository resumes;
    private final AgentPlannerClient planner;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AgentRunService(AgentRunRepository runs, AgentStepRepository steps,
                           CandidateResumeService resumeService, ResumeRepository resumes,
                           AgentPlannerClient planner,
                           AgentProperties properties, ObjectMapper mapper, Clock clock) {
        this.runs = runs;
        this.steps = steps;
        this.resumeService = resumeService;
        this.resumes = resumes;
        this.planner = planner;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public AgentDtos.RunResponse create(AuthenticatedUser principal, AgentDtos.CreateRunRequest request) {
        requireCandidate(principal);
        Instant now = clock.instant();
        String conversationId = conversationId(principal, request.conversationId());
        List<ConversationMessage> history = plannerHistory(principal.userId(), conversationId);
        AgentRunEntity run = runs.save(new AgentRunEntity(
                UUID.randomUUID().toString(), principal.userId(), conversationId, request.instruction().trim(),
                now));

        PlanResponse plan;
        long planStarted = System.nanoTime();
        try {
            plan = planner.plan(run.getInstruction(), "CANDIDATE", null, null, null, history);
            addStep(run, 1, AgentStepType.PLAN, null, "instruction received",
                    summarizePlan(plan), AgentStepStatus.SUCCEEDED, null, elapsedMillis(planStarted));
        } catch (PlannerException exception) {
            addStep(run, 1, AgentStepType.PLAN, null, "instruction received", null,
                    AgentStepStatus.FAILED, exception.getCode(), elapsedMillis(planStarted));
            if ("AGENT_PLAN_REJECTED".equals(exception.getCode())) {
                run.needsClarification("Please provide a supported resume instruction with a concrete value: "
                        + "age (16-100), summary text, skills, or experiences.", clock.instant());
            } else {
                run.fail(exception.getCode(), "The agent could not prepare a plan. Please try again.", clock.instant());
            }
            return response(run);
        }

        if ("CHAT".equals(plan.status())) {
            if (!"CHAT".equals(plan.intent()) || plan.target() != null
                    || plan.operations() == null || !plan.operations().isEmpty()) {
                run.fail("AGENT_PLAN_REJECTED", "The generated chat response is not allowed.", clock.instant());
                addStep(run, 2, AgentStepType.SYSTEM, null, "validate chat response", null,
                        AgentStepStatus.FAILED, "AGENT_PLAN_REJECTED", 0);
                return response(run);
            }
            run.completeChat(safeMessage(plan.message(),
                    "How can I help with your resume or job search?"), clock.instant());
            return response(run);
        }

        if (!"READY".equals(plan.status())) {
            run.needsClarification(safeMessage(plan.message(),
                    "Please provide a supported resume age update instruction."), clock.instant());
            return response(run);
        }

        boolean query = "QUERY_RESUME".equals(plan.intent());
        boolean update = "UPDATE_RESUME".equals(plan.intent());
        Set<String> expectedTools = query ? QUERY_TOOLS : update ? UPDATE_TOOLS : Set.of();
        if (!"DEFAULT_RESUME".equals(plan.target()) || !hasExactTools(plan, expectedTools)) {
            run.fail("AGENT_PLAN_REJECTED", "The generated plan is not allowed.", clock.instant());
            addStep(run, 2, AgentStepType.SYSTEM, null, "validate tool whitelist", null,
                    AgentStepStatus.FAILED, "AGENT_PLAN_REJECTED", 0);
            return response(run);
        }
        long readStarted = System.nanoTime();
        if (!resumes.existsByCandidateId(principal.userId())) {
            addStep(run, 2, AgentStepType.TOOL, GET_RESUME, "current candidate", null,
                    AgentStepStatus.FAILED, "NOT_FOUND", elapsedMillis(readStarted));
            run.fail("NOT_FOUND", "A default resume is required before this instruction can be previewed.",
                    clock.instant());
            return response(run);
        }
        try {
            var resume = resumeService.get(principal).data();
            addStep(run, 2, AgentStepType.TOOL, GET_RESUME, "current candidate",
                    "resumeId=" + resume.resumeId() + "; version=" + resume.version(),
                    AgentStepStatus.SUCCEEDED, null, elapsedMillis(readStarted));

            if (query) {
                PlanOperation read = operation(plan, READ_SECTION);
                String section = stringArgument(read.arguments(), "section");
                AgentDtos.QueryResult queryResult = queryResult(section, resume);
                if (queryResult == null) throw new PatchClarification("Which resume section should be shown?");
                Instant completedAt = clock.instant();
                AgentDtos.ExecutionResult result = new AgentDtos.ExecutionResult(
                        "READ_RESUME", "RESUME", resume.resumeId(), resume.version(), resume.version(), completedAt,
                        List.of(), queryResult);
                addStep(run, 3, AgentStepType.TOOL, READ_SECTION, "section=" + section,
                        "resume section returned", AgentStepStatus.SUCCEEDED, null, 0);
                run.completeRead("RESUME", resume.resumeId(), writeJson(result),
                        queryMessage(section), completedAt);
                return response(run);
            }

            PlanOperation previewPatch = operation(plan, PREVIEW_PATCH);
            AgentDtos.FieldChange change = buildChange(resume, previewPatch.arguments());
            if (Objects.equals(change.oldValue(), change.newValue())) {
                String field = change.field();
                String message = "age".equals(field)
                        ? "Your default resume age is already " + change.newValue() + ", so no change is needed."
                        : "Your resume " + field + " already matches this request, so no change is needed.";
                run.noActionRequired("RESUME", resume.resumeId(),
                        message, clock.instant());
                addStep(run, 3, AgentStepType.TOOL, PREVIEW_PATCH, "field=" + field,
                        "no change required", AgentStepStatus.SUCCEEDED, null, 0);
                return response(run);
            }

            Instant expiresAt = clock.instant().plusSeconds(properties.previewTtlSeconds());
            String confirmationId = UUID.randomUUID().toString();
            AgentDtos.Preview preview = new AgentDtos.Preview(confirmationId, "RESUME", resume.resumeId(),
                    resume.version(), expiresAt, List.of(change));
            addStep(run, 3, AgentStepType.TOOL, PREVIEW_PATCH,
                    "targetId=" + resume.resumeId() + "; field=" + change.field()
                            + "; expectedVersion=" + resume.version(),
                    "one field change prepared", AgentStepStatus.SUCCEEDED, null, 0);
            run.awaitingConfirmation(preview.targetType(), preview.targetId(), writeJson(preview),
                    preview.confirmationId(), preview.expiresAt(),
                    safeMessage(plan.message(), "Review the proposed resume change."), clock.instant());
            return response(run);
        } catch (PatchClarification exception) {
            run.needsClarification(exception.getMessage(), clock.instant());
            addStep(run, 3, AgentStepType.SYSTEM, PREVIEW_PATCH, "validate resume request", null,
                    AgentStepStatus.FAILED, "AGENT_PATCH_VALIDATION_FAILED", 0);
            return response(run);
        } catch (RuntimeException exception) {
            addStep(run, 2, AgentStepType.TOOL, GET_RESUME, "current candidate", null,
                    AgentStepStatus.FAILED, "AGENT_TOOL_FAILED", elapsedMillis(readStarted));
            run.fail("AGENT_TOOL_FAILED", "The agent could not read the resume.", clock.instant());
            return response(run);
        }
    }

    @Transactional
    public ConfirmResult confirm(AuthenticatedUser principal, String runId, String rawIdempotencyKey,
                                 AgentDtos.ConfirmRunRequest request) {
        requireCandidate(principal);
        String idempotencyKey = requireUuid(rawIdempotencyKey, "Idempotency-Key");
        AgentRunEntity run = runs.findOwnedForUpdate(runId, principal.userId()).orElseThrow(AgentRunService::notFound);

        if (run.getStatus() == AgentRunStatus.COMPLETED) {
            if (idempotencyKey.equals(run.getExecutionIdempotencyKey())
                    && request.confirmationId().equals(run.getConfirmationId())) {
                return new ConfirmResult(HttpStatus.OK, response(run));
            }
            throw conflict("AGENT_CONFIRMATION_ALREADY_USED", "The agent run was already confirmed");
        }
        if (run.getStatus() != AgentRunStatus.AWAITING_CONFIRMATION
                || run.getConfirmationStatus() != com.adproject.agent.domain.AgentConfirmationStatus.PENDING) {
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
        AgentDtos.FieldChange change = preview == null || preview.changes() == null || preview.changes().size() != 1
                ? null : preview.changes().getFirst();
        if (preview == null || change == null || !"RESUME".equals(preview.targetType())
                || !Set.of("age", "summary", "skills", "experiences").contains(change.field())
                || run.getTargetId() == null || !run.getTargetId().equals(preview.targetId())) {
            run.rejectExecution("AGENT_PREVIEW_INVALID", "The stored change preview is invalid.", now);
            addStep(run, sequence, AgentStepType.SYSTEM, null, "validate stored preview", null,
                    AgentStepStatus.FAILED, "AGENT_PREVIEW_INVALID", 0);
            return new ConfirmResult(HttpStatus.CONFLICT, response(run));
        }

        var currentResume = resumes.findByIdForUpdate(preview.targetId())
                .filter(value -> value.getCandidateId().equals(principal.userId())).orElse(null);
        if (currentResume == null) {
            run.rejectExecution("NOT_FOUND", "The target resume no longer exists.", now);
            addStep(run, sequence, AgentStepType.TOOL, APPLY_PATCH, "targetId=" + preview.targetId(), null,
                    AgentStepStatus.FAILED, "NOT_FOUND", 0);
            return new ConfirmResult(HttpStatus.CONFLICT, response(run));
        }
        Resume current = resumeService.get(principal).data();
        if (currentResume.getVersion() != preview.expectedVersion() || !matchesOldValue(current, change)) {
            run.rejectExecution("VERSION_CONFLICT", "The resume changed after the preview was created.", now);
            addStep(run, sequence, AgentStepType.TOOL, APPLY_PATCH,
                    "targetId=" + preview.targetId() + "; expectedVersion=" + preview.expectedVersion(), null,
                    AgentStepStatus.FAILED, "VERSION_CONFLICT", 0);
            return new ConfirmResult(HttpStatus.CONFLICT, response(run));
        }

        run.startExecution(idempotencyKey, now);
        addStep(run, sequence++, AgentStepType.SYSTEM, null,
                "confirmationId=" + request.confirmationId() + "; expectedRunVersion=" + request.expectedRunVersion(),
                "confirmation accepted", AgentStepStatus.SUCCEEDED, null, 0);
        long applyStarted = System.nanoTime();
        Resume savedResume;
        try {
            savedResume = applyChange(principal, current, preview, change);
        } catch (RuntimeException exception) {
            run.rejectExecution("AGENT_PREVIEW_INVALID", "The stored change preview is invalid.", now);
            addStep(run, sequence, AgentStepType.TOOL, APPLY_PATCH,
                    "targetId=" + preview.targetId() + "; field=" + change.field(), null,
                    AgentStepStatus.FAILED, "AGENT_PREVIEW_INVALID", elapsedMillis(applyStarted));
            return new ConfirmResult(HttpStatus.CONFLICT, response(run));
        }
        addStep(run, sequence, AgentStepType.TOOL, APPLY_PATCH,
                "targetId=" + preview.targetId() + "; field=" + change.field()
                        + "; expectedVersion=" + preview.expectedVersion(),
                "resumeVersion=" + savedResume.version(), AgentStepStatus.SUCCEEDED, null,
                elapsedMillis(applyStarted));
        Instant completedAt = clock.instant();
        AgentDtos.ExecutionResult result = new AgentDtos.ExecutionResult(
                "UPDATE_RESUME", "RESUME", savedResume.resumeId(), preview.expectedVersion(),
                savedResume.version(), completedAt, preview.changes(), null);
        run.complete(writeJson(result), "Your resume " + change.field() + " was updated successfully.", completedAt);
        return new ConfirmResult(HttpStatus.OK, response(run));
    }

    @Transactional(readOnly = true)
    public AgentDtos.RunResponse get(AuthenticatedUser principal, String runId) {
        requireCandidate(principal);
        AgentRunEntity run = runs.findByIdAndUserId(runId, principal.userId()).orElseThrow(AgentRunService::notFound);
        return response(run);
    }

    @Transactional(readOnly = true)
    public AgentDtos.ConversationResponse recentConversation(AuthenticatedUser principal) {
        requireCandidate(principal);
        return runs.findFirstByUserIdOrderByUpdatedAtDesc(principal.userId())
                .map(run -> conversationResponse(principal.userId(), run.getConversationId()))
                .orElseGet(() -> new AgentDtos.ConversationResponse(
                        new AgentDtos.Conversation(null, List.of())));
    }

    @Transactional(readOnly = true)
    public AgentDtos.ConversationListResponse listConversations(AuthenticatedUser principal) {
        requireCandidate(principal);
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
        requireCandidate(principal);
        String conversationId = requireUuid(rawConversationId, "conversationId");
        if (!runs.existsByConversationIdAndUserId(conversationId, principal.userId())) throw notFound();
        return conversationResponse(principal.userId(), conversationId);
    }

    @Transactional
    public AgentDtos.RunResponse cancel(AuthenticatedUser principal, String runId) {
        requireCandidate(principal);
        AgentRunEntity run = runs.findOwnedForUpdate(runId, principal.userId()).orElseThrow(AgentRunService::notFound);
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

    private AgentDtos.RunResponse response(AgentRunEntity run) {
        AgentDtos.Target target = run.getTargetId() == null ? null
                : new AgentDtos.Target(run.getTargetType(), run.getTargetId());
        List<AgentDtos.Step> runSteps = steps.findByRunIdOrderBySequenceNoAsc(run.getId()).stream()
                .map(step -> new AgentDtos.Step(step.getSequenceNo(), step.getStepType().name(), step.getToolName(),
                        step.getStatus().name(), step.getInputSummary(), step.getOutputSummary(), step.getErrorCode(),
                        step.getDurationMs(), step.getCreatedAt()))
                .toList();
        AgentDtos.Preview preview = readPreview(run.getPreviewJson());
        AgentDtos.ExecutionResult result = readResult(run.getResultJson());
        return new AgentDtos.RunResponse(new AgentDtos.Run(run.getId(), run.getConversationId(),
                run.getInstruction(), run.getStatus().name(),
                run.getConfirmationStatus().name(), target, runSteps, preview, result, null,
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

    private AgentDtos.QueryResult queryResult(String section, Resume resume) {
        return switch (section == null ? "" : section) {
            case "summary" -> new AgentDtos.QueryResult("summary", resume.summary(), null, null);
            case "skills" -> new AgentDtos.QueryResult("skills", null, resume.skills(), null);
            case "experiences" -> new AgentDtos.QueryResult("experiences", null, null, resume.experiences());
            default -> null;
        };
    }

    private String queryMessage(String section) {
        return switch (section == null ? "" : section) {
            case "summary" -> "Here is the summary from your default resume.";
            case "skills" -> "Here are the skills from your default resume.";
            case "experiences" -> "Here are the experiences from your default resume.";
            default -> "Here is the requested resume information.";
        };
    }

    private AgentDtos.FieldChange buildChange(Resume resume, Map<String, Object> arguments) {
        if (arguments == null) throw new PatchClarification("Please provide a supported resume change.");
        String field = stringArgument(arguments, "field");
        String action = stringArgument(arguments, "action");
        if (field == null || action == null) throw new PatchClarification("Please provide a supported resume change.");
        return switch (field) {
            case "age" -> ageChange(resume, action, arguments);
            case "summary" -> summaryChange(resume, action, arguments);
            case "skills" -> skillsChange(resume, action, arguments);
            case "experiences" -> experiencesChange(resume, action, arguments);
            default -> throw new PatchClarification(
                    "This Agent can update resume age, summary, skills, and experiences.");
        };
    }

    private AgentDtos.FieldChange ageChange(Resume resume, String action, Map<String, Object> arguments) {
        Integer value = integerValue(arguments.get("value"));
        if (!"set".equals(action) || value == null || value < 16 || value > 100) {
            throw new PatchClarification("Please provide an age between 16 and 100.");
        }
        return new AgentDtos.FieldChange("age", resume.age(), value);
    }

    private AgentDtos.FieldChange summaryChange(Resume resume, String action, Map<String, Object> arguments) {
        String value = stringArgument(arguments, "value");
        if (!"set".equals(action) || value == null || value.isBlank()) {
            throw new PatchClarification("What summary should be set on your default resume?");
        }
        value = value.trim();
        if (value.length() > 5000) throw new PatchClarification("The summary must not exceed 5000 characters.");
        return new AgentDtos.FieldChange("summary", resume.summary(), value);
    }

    private AgentDtos.FieldChange skillsChange(Resume resume, String action, Map<String, Object> arguments) {
        List<String> current = new ArrayList<>(resume.skills());
        List<String> updated = new ArrayList<>(current);
        switch (action) {
            case "add" -> {
                List<String> values = stringListArgument(arguments, "values");
                for (String value : values) {
                    if (findIgnoreCase(updated, value) < 0) updated.add(value);
                }
            }
            case "delete" -> {
                List<String> values = stringListArgument(arguments, "values");
                for (String value : values) {
                    int index = findIgnoreCase(updated, value);
                    if (index < 0) throw new PatchClarification("The skill '" + value + "' is not on your resume.");
                    updated.remove(index);
                }
            }
            case "update" -> {
                String oldValue = stringArgument(arguments, "oldValue");
                String newValue = stringArgument(arguments, "newValue");
                if (oldValue == null || oldValue.isBlank() || newValue == null || newValue.isBlank()) {
                    throw new PatchClarification("Which skill should be renamed, and what should replace it?");
                }
                int index = findIgnoreCase(updated, oldValue);
                if (index < 0) throw new PatchClarification("The skill '" + oldValue + "' is not on your resume.");
                int duplicate = findIgnoreCase(updated, newValue);
                if (duplicate >= 0 && duplicate != index) {
                    throw new PatchClarification("The skill '" + newValue + "' is already on your resume.");
                }
                updated.set(index, newValue.trim());
            }
            default -> throw new PatchClarification("Skills support add, delete, and update requests.");
        }
        if (updated.size() > 100) throw new PatchClarification("A resume can contain at most 100 skills.");
        return new AgentDtos.FieldChange("skills", current, updated);
    }

    private AgentDtos.FieldChange experiencesChange(Resume resume, String action, Map<String, Object> arguments) {
        List<Experience> current = new ArrayList<>(resume.experiences());
        List<Experience> updated = new ArrayList<>(current);
        switch (action) {
            case "add" -> {
                Experience experience = experienceArgument(arguments.get("experience"), true);
                updated.add(experience);
            }
            case "delete" -> updated.remove(findExperience(updated, stringArgument(arguments, "selector")));
            case "update" -> {
                int index = findExperience(updated, stringArgument(arguments, "selector"));
                Experience existing = updated.get(index);
                Object rawChanges = arguments.get("changes");
                if (!(rawChanges instanceof Map<?, ?> changes) || changes.isEmpty()) {
                    throw new PatchClarification("Provide at least one experience field to update.");
                }
                Set<String> allowed = Set.of("title", "company", "description", "startDate", "endDate");
                if (!changes.keySet().stream().allMatch(key -> allowed.contains(String.valueOf(key)))) {
                    throw new PatchClarification("An experience can update title, company, description, startDate, or endDate.");
                }
                Experience replacement = new Experience(existing.experienceId(),
                        changedString(changes, "title", existing.title()),
                        changedString(changes, "company", existing.company()),
                        changedString(changes, "description", existing.description()),
                        changedString(changes, "startDate", existing.startDate()),
                        changedNullableString(changes, "endDate", existing.endDate()));
                validateExperience(replacement);
                updated.set(index, replacement);
            }
            default -> throw new PatchClarification("Experiences support add, delete, and update requests.");
        }
        return new AgentDtos.FieldChange("experiences", current, updated);
    }

    private Experience experienceArgument(Object value, boolean assignId) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new PatchClarification("Provide the experience title, company, description, and start date.");
        }
        Experience experience = new Experience(assignId ? UUID.randomUUID().toString() : stringValue(map.get("experienceId")),
                stringValue(map.get("title")), stringValue(map.get("company")), stringValue(map.get("description")),
                stringValue(map.get("startDate")), nullableStringValue(map.get("endDate")));
        validateExperience(experience);
        return experience;
    }

    private void validateExperience(Experience experience) {
        if (experience.title() == null || experience.title().isBlank() || experience.company() == null
                || experience.company().isBlank() || experience.description() == null
                || experience.description().isBlank()) {
            throw new PatchClarification("Experience title, company, and description are required.");
        }
        YearMonth start = yearMonth(experience.startDate(), "start date");
        YearMonth end = experience.endDate() == null || experience.endDate().isBlank()
                ? null : yearMonth(experience.endDate(), "end date");
        if (end != null && end.isBefore(start)) {
            throw new PatchClarification("The experience end date cannot be before its start date.");
        }
    }

    private int findExperience(List<Experience> experiences, String selector) {
        if (selector == null || selector.isBlank()) {
            throw new PatchClarification("Provide the experience ID, title, company, or list number.");
        }
        String normalized = selector.trim();
        String digits = normalized.replaceAll("^第?(\\d+)(?:段|条|个)?$", "$1");
        if (digits.matches("\\d+")) {
            int index = Integer.parseInt(digits) - 1;
            if (index >= 0 && index < experiences.size()) return index;
            throw new PatchClarification("That experience number does not exist.");
        }
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < experiences.size(); index++) {
            Experience experience = experiences.get(index);
            if (equalsIgnoreCase(experience.experienceId(), normalized)
                    || equalsIgnoreCase(experience.title(), normalized)
                    || equalsIgnoreCase(experience.company(), normalized)) {
                matches.add(index);
            }
        }
        if (matches.isEmpty()) throw new PatchClarification("No experience matched '" + normalized + "'.");
        if (matches.size() > 1) {
            throw new PatchClarification("More than one experience matched '" + normalized + "'. Use its ID or number.");
        }
        return matches.getFirst();
    }

    private boolean matchesOldValue(Resume current, AgentDtos.FieldChange change) {
        try {
            return switch (change.field()) {
                case "age" -> Objects.equals(current.age(), integerValue(change.oldValue()));
                case "summary" -> Objects.equals(current.summary(), stringValue(change.oldValue()));
                case "skills" -> Objects.equals(current.skills(), mapper.convertValue(change.oldValue(), STRING_LIST));
                case "experiences" -> Objects.equals(current.experiences(),
                        mapper.convertValue(change.oldValue(), EXPERIENCE_LIST));
                default -> false;
            };
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Resume applyChange(AuthenticatedUser principal, Resume current, AgentDtos.Preview preview,
                               AgentDtos.FieldChange change) {
        return switch (change.field()) {
            case "age" -> {
                Integer age = integerValue(change.newValue());
                if (age == null || age < 16 || age > 100) throw new IllegalArgumentException("invalid age");
                yield resumeService.patchAge(principal, preview.targetId(), age, preview.expectedVersion()).data();
            }
            case "summary" -> resumeService.patchContent(principal, preview.targetId(), preview.expectedVersion(),
                    requiredString(change.newValue()), current.skills(), current.experiences()).data();
            case "skills" -> resumeService.patchContent(principal, preview.targetId(), preview.expectedVersion(),
                    current.summary(), mapper.convertValue(change.newValue(), STRING_LIST), current.experiences()).data();
            case "experiences" -> resumeService.patchContent(principal, preview.targetId(), preview.expectedVersion(),
                    current.summary(), current.skills(), mapper.convertValue(change.newValue(), EXPERIENCE_LIST)).data();
            default -> throw new IllegalArgumentException("unsupported field");
        };
    }

    private List<String> stringListArgument(Map<String, Object> arguments, String key) {
        Object raw = arguments.get(key);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new PatchClarification("Provide at least one skill.");
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            String value = stringValue(item);
            if (value == null || value.isBlank() || value.trim().length() > 200) {
                throw new PatchClarification("Each skill must contain 1 to 200 characters.");
            }
            values.add(value.trim());
        }
        return values;
    }

    private static int findIgnoreCase(List<String> values, String target) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).equalsIgnoreCase(target.trim())) return index;
        }
        return -1;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String changedString(Map<?, ?> values, String key, String fallback) {
        return values.containsKey(key) ? requiredString(values.get(key)) : fallback;
    }

    private static String changedNullableString(Map<?, ?> values, String key, String fallback) {
        return values.containsKey(key) ? nullableStringValue(values.get(key)) : fallback;
    }

    private static String stringArgument(Map<String, Object> arguments, String key) {
        return arguments == null ? null : stringValue(arguments.get(key));
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String nullableStringValue(Object value) {
        if (value == null) return null;
        String text = stringValue(value);
        if (text == null) throw new IllegalArgumentException("expected string");
        return text.isBlank() || Set.of("至今", "当前", "present", "null").contains(text.toLowerCase()) ? null : text;
    }

    private static String requiredString(Object value) {
        String text = stringValue(value);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("expected non-blank string");
        return text;
    }

    private static YearMonth yearMonth(String value, String label) {
        try {
            if (value == null || !value.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                throw new DateTimeParseException("invalid", value == null ? "" : value, 0);
            }
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new PatchClarification("The experience " + label + " must use YYYY-MM.");
        }
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

    private int nextSequence(String runId) {
        return steps.findByRunIdOrderBySequenceNoAsc(runId).size() + 1;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
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

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Agent run not found");
    }

    private static final class PatchClarification extends RuntimeException {
        private PatchClarification(String message) {
            super(message);
        }
    }
}
