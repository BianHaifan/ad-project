package com.adproject.conversation.application;

import com.adproject.application.domain.ApplicationStatus;
import com.adproject.application.infrastructure.ApplicationEntity;
import com.adproject.application.infrastructure.ApplicationRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.conversation.api.ConversationDtos;
import com.adproject.conversation.api.ConversationDtos.Detail;
import com.adproject.conversation.api.ConversationDtos.DetailResponse;
import com.adproject.conversation.api.ConversationDtos.ListResponse;
import com.adproject.conversation.api.ConversationDtos.Message;
import com.adproject.conversation.api.ConversationDtos.MessageListResponse;
import com.adproject.conversation.api.ConversationDtos.MessageResponse;
import com.adproject.conversation.api.ConversationDtos.PageMeta;
import com.adproject.conversation.api.ConversationDtos.Participant;
import com.adproject.conversation.api.ConversationDtos.ReadStateRequest;
import com.adproject.conversation.api.ConversationDtos.SendMessageRequest;
import com.adproject.conversation.api.ConversationDtos.Summary;
import com.adproject.conversation.domain.SenderType;
import com.adproject.conversation.infrastructure.ConversationEntity;
import com.adproject.conversation.infrastructure.ConversationReadStateEntity;
import com.adproject.conversation.infrastructure.ConversationReadStateId;
import com.adproject.conversation.infrastructure.ConversationReadStateRepository;
import com.adproject.conversation.infrastructure.ConversationRepository;
import com.adproject.conversation.infrastructure.MessageEntity;
import com.adproject.conversation.infrastructure.MessageRepository;
import com.adproject.job.infrastructure.JobEntity;
import com.adproject.job.infrastructure.JobRepository;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import jakarta.persistence.criteria.Subquery;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    private static final int MAX_BODY_LENGTH = 5000;
    private static final String DELIVERY_SENT = "SENT";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationReadStateRepository readStates;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               ConversationReadStateRepository readStates, ApplicationRepository applications,
                               JobRepository jobs, UserRepository users, CandidateProfileRepository profiles,
                               CompanyRepository companies, CompanyMemberRepository members, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.readStates = readStates;
        this.applications = applications;
        this.jobs = jobs;
        this.users = users;
        this.profiles = profiles;
        this.companies = companies;
        this.members = members;
        this.clock = clock;
    }

    // ---- Candidate endpoints ----

    @Transactional(readOnly = true)
    public ListResponse listCandidate(AuthenticatedUser principal, int page, int pageSize) {
        requireCandidate(principal);
        var result = conversations.findByCandidateId(principal.userId(),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        var data = result.getContent().stream().map(c -> summary(c, principal.userId())).toList();
        return new ListResponse(data, new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public DetailResponse detailCandidate(AuthenticatedUser principal, String conversationId) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return new DetailResponse(detail(conversation, principal.userId()));
    }

    @Transactional(readOnly = true)
    public MessageListResponse listMessagesCandidate(AuthenticatedUser principal, String conversationId,
                                                     String before, int limit) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return listMessages(conversation, before, limit);
    }

    @Transactional
    public MessageResponse sendCandidate(AuthenticatedUser principal, String conversationId, String idempotencyKey,
                                         SendMessageRequest request) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        return send(conversation, principal, idempotencyKey, request);
    }

    @Transactional
    public void updateReadStateCandidate(AuthenticatedUser principal, String conversationId, ReadStateRequest request) {
        ConversationEntity conversation = requireCandidateConversation(principal, conversationId);
        updateReadState(conversation, principal.userId(), request);
    }

    // ---- Recruiter endpoints ----

    @Transactional(readOnly = true)
    public ListResponse listRecruiter(AuthenticatedUser principal, String q, boolean unreadOnly, int page, int pageSize) {
        String companyId = requireCompany(principal);
        Specification<ConversationEntity> specification = (root, query, cb) ->
                cb.equal(root.get("companyId"), companyId);
        if (q != null && !q.isBlank()) {
            String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> {
                Subquery<String> candidates = query.subquery(String.class);
                var user = candidates.from(UserEntity.class);
                candidates.select(user.get("id")).where(cb.or(
                        cb.like(cb.lower(user.get("fullName")), value),
                        cb.like(cb.lower(user.get("email")), value)));
                return root.get("candidateId").in(candidates);
            });
        }
        var result = conversations.findAll(specification, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        List<Summary> data = result.getContent().stream().map(c -> summary(c, principal.userId())).toList();
        if (unreadOnly) {
            data = data.stream().filter(s -> s.unreadCount() > 0).toList();
        }
        return new ListResponse(data, new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public DetailResponse detailRecruiter(AuthenticatedUser principal, String conversationId) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return new DetailResponse(detail(conversation, principal.userId()));
    }

    @Transactional(readOnly = true)
    public MessageListResponse listMessagesRecruiter(AuthenticatedUser principal, String conversationId,
                                                     String before, int limit) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return listMessages(conversation, before, limit);
    }

    @Transactional
    public MessageResponse sendRecruiter(AuthenticatedUser principal, String conversationId, String idempotencyKey,
                                         SendMessageRequest request) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        return send(conversation, principal, idempotencyKey, request);
    }

    @Transactional
    public void updateReadStateRecruiter(AuthenticatedUser principal, String conversationId, ReadStateRequest request) {
        ConversationEntity conversation = requireRecruiterConversation(principal, conversationId);
        updateReadState(conversation, principal.userId(), request);
    }

    // ---- Shared internals ----

    private MessageListResponse listMessages(ConversationEntity conversation, String before, int limit) {
        Instant beforeSentAt = null;
        String beforeId = null;
        if (before != null && !before.isBlank()) {
            MessageEntity cursor = messages.findById(before)
                    .filter(m -> m.getConversationId().equals(conversation.getId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                            "Request validation failed", Map.of("before", "must be a message in this conversation")));
            beforeSentAt = cursor.getSentAt();
            beforeId = cursor.getId();
        }
        List<MessageEntity> desc = messages.pageBefore(conversation.getId(), beforeSentAt, beforeId,
                PageRequest.of(0, limit + 1));
        boolean hasMore = desc.size() > limit;
        List<MessageEntity> page = hasMore ? desc.subList(0, limit) : desc;
        String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        List<Message> data = new ArrayList<>(page.stream().map(this::message).toList());
        Collections.reverse(data);
        return new MessageListResponse(data, new ConversationDtos.MessageListMeta(nextCursor, hasMore));
    }

    private MessageResponse send(ConversationEntity conversation, AuthenticatedUser principal, String idempotencyKey,
                                 SendMessageRequest request) {
        String body = request.body() == null ? "" : request.body();
        if (body.isBlank()) {
            throw validation("body", "must not be blank");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw validation("body", "must be at most " + MAX_BODY_LENGTH + " characters");
        }
        String clientMessageId = requireUuid(request.clientMessageId(), "clientMessageId");
        String key = requireUuid(idempotencyKey, "Idempotency-Key");
        String payloadHash = digest(conversation.getId() + "\n" + clientMessageId + "\n" + body);

        var byKey = messages.findBySenderIdAndIdempotencyKey(principal.userId(), key);
        if (byKey.isPresent()) {
            if (byKey.get().getPayloadHash().equals(payloadHash)) {
                return new MessageResponse(message(byKey.get()));
            }
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used with a different request");
        }

        var byClient = messages.findByConversationIdAndClientMessageId(conversation.getId(), clientMessageId);
        if (byClient.isPresent()) {
            return new MessageResponse(message(byClient.get()));
        }

        ApplicationEntity application = applications.findById(conversation.getApplicationId()).orElseThrow(this::notFound);
        if (application.getStatus() == ApplicationStatus.REJECTED || application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_CLOSED",
                    "This conversation is read-only because the application is no longer active");
        }

        Instant now = clock.instant();
        MessageEntity entity = messages.save(new MessageEntity(UUID.randomUUID().toString(), conversation.getId(),
                principal.userId(), senderType(principal.role()), body, now, clientMessageId, key, payloadHash));
        conversation.touch(now);
        conversations.flush();
        return new MessageResponse(message(entity));
    }

    private void updateReadState(ConversationEntity conversation, String userId, ReadStateRequest request) {
        String messageId = request.lastReadMessageId();
        if (messageId == null || messageId.isBlank()) {
            throw validation("lastReadMessageId", "is required");
        }
        MessageEntity target = messages.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversation.getId()))
                .orElseThrow(() -> validation("lastReadMessageId", "must be a message in this conversation"));
        Instant now = clock.instant();
        ConversationReadStateEntity state = readStates.findById(new ConversationReadStateId(conversation.getId(), userId))
                .orElse(new ConversationReadStateEntity(conversation.getId(), userId, target.getId(), now));
        state.update(target.getId(), now);
        readStates.save(state);
    }

    private Summary summary(ConversationEntity conversation, String viewerId) {
        boolean viewerIsCandidate = conversation.getCandidateId().equals(viewerId);
        return new Summary(conversation.getId(), conversation.getApplicationId(), conversation.getJobId(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(),
                viewerIsCandidate ? recruiterParticipant(conversation) : candidateParticipant(conversation),
                lastMessage(conversation.getId()), unreadCount(conversation.getId(), viewerId),
                jobTitle(conversation.getJobId()));
    }

    private Detail detail(ConversationEntity conversation, String viewerId) {
        boolean viewerIsCandidate = conversation.getCandidateId().equals(viewerId);
        return new Detail(conversation.getId(), conversation.getApplicationId(), conversation.getJobId(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(),
                viewerIsCandidate ? recruiterParticipant(conversation) : candidateParticipant(conversation), null);
    }

    private Participant recruiterParticipant(ConversationEntity conversation) {
        JobEntity job = jobs.findById(conversation.getJobId()).orElseThrow(this::notFound);
        String recruiterId = job.getOwnerId() != null ? job.getOwnerId() : job.getCreatedBy();
        UserEntity recruiter = users.findById(recruiterId).orElseThrow(this::notFound);
        CompanyEntity company = companies.findById(conversation.getCompanyId()).orElseThrow(this::notFound);
        return new Participant(recruiter.getId(), recruiter.getFullName(), recruiter.getAvatarUrl(), null,
                company(company), false);
    }

    private Participant candidateParticipant(ConversationEntity conversation) {
        UserEntity candidate = users.findById(conversation.getCandidateId()).orElseThrow(this::notFound);
        var profile = profiles.findById(candidate.getId()).orElse(null);
        return new Participant(candidate.getId(), candidate.getFullName(), candidate.getAvatarUrl(),
                profile == null ? null : profile.getHeadline(), null, false);
    }

    private Message lastMessage(String conversationId) {
        return messages.findLatest(conversationId, PageRequest.of(0, 1)).stream().findFirst()
                .map(this::message).orElse(null);
    }

    private long unreadCount(String conversationId, String userId) {
        Instant lastReadAt = readStates.findById(new ConversationReadStateId(conversationId, userId))
                .flatMap(s -> messages.findById(s.getLastReadMessageId()))
                .map(MessageEntity::getSentAt).orElse(null);
        return messages.countUnread(conversationId, userId, lastReadAt);
    }

    private String jobTitle(String jobId) {
        return jobs.findById(jobId).map(JobEntity::getTitle).orElseThrow(this::notFound);
    }

    private Message message(MessageEntity entity) {
        return new Message(entity.getId(), entity.getConversationId(), entity.getBody(), entity.getSenderType().name(),
                entity.getSentAt(), entity.getClientMessageId(), DELIVERY_SENT);
    }

    private static ConversationDtos.Company company(CompanyEntity company) {
        return new ConversationDtos.Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus().name(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private ConversationEntity requireCandidateConversation(AuthenticatedUser principal, String conversationId) {
        requireCandidate(principal);
        return conversations.findById(conversationId)
                .filter(c -> c.getCandidateId().equals(principal.userId()))
                .orElseThrow(this::notFound);
    }

    private ConversationEntity requireRecruiterConversation(AuthenticatedUser principal, String conversationId) {
        String companyId = requireCompany(principal);
        return conversations.findById(conversationId)
                .filter(c -> c.getCompanyId().equals(companyId))
                .orElseThrow(this::notFound);
    }

    private String requireCompany(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.RECRUITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
        return members.findByUserId(principal.userId()).map(CompanyMemberEntity::getCompanyId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission"));
    }

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static SenderType senderType(UserRole role) {
        return role == UserRole.CANDIDATE ? SenderType.CANDIDATE : SenderType.RECRUITER;
    }

    private static String requireUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                    Map.of(field, "is required"));
        }
        try { return UUID.fromString(value).toString(); }
        catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                    Map.of(field, "must be a UUID"));
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static ApiException validation(String field, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                Map.of(field, detail));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Conversation not found");
    }
}
