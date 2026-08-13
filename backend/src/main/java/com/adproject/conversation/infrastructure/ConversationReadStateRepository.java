package com.adproject.conversation.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationReadStateRepository
        extends JpaRepository<ConversationReadStateEntity, ConversationReadStateId> {
}
