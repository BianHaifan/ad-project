package com.adproject.conversation.application;

import com.adproject.conversation.infrastructure.ConversationEntity;
import com.adproject.conversation.infrastructure.ConversationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public provisioning entry point used by the application module. Creating a conversation is part of the
 * application-submission transaction and must be idempotent: one application maps to at most one conversation.
 */
@Service
public class ConversationProvisioningService {
    private final ConversationRepository conversations;

    public ConversationProvisioningService(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    @Transactional
    public String provision(String applicationId, String jobId, String candidateId, String companyId, Instant now) {
        return conversations.findByApplicationId(applicationId)
                .map(ConversationEntity::getId)
                .orElseGet(() -> conversations.save(new ConversationEntity(
                        UUID.randomUUID().toString(), applicationId, jobId, candidateId, companyId, now, now)).getId());
    }
}
