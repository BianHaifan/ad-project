package com.adproject.conversation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversation_read_states")
@IdClass(ConversationReadStateId.class)
public class ConversationReadStateEntity {
    @Id @Column(name = "conversation_id", nullable = false, length = 36, columnDefinition = "char(36)") private String conversationId;
    @Id @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "char(36)") private String userId;
    @Column(name = "last_read_message_id", nullable = false, length = 36, columnDefinition = "char(36)") private String lastReadMessageId;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ConversationReadStateEntity() {}

    public ConversationReadStateEntity(String conversationId, String userId, String lastReadMessageId, Instant updatedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.lastReadMessageId = lastReadMessageId;
        this.updatedAt = updatedAt;
    }

    public String getConversationId() { return conversationId; }
    public String getUserId() { return userId; }
    public String getLastReadMessageId() { return lastReadMessageId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String lastReadMessageId, Instant now) {
        this.lastReadMessageId = lastReadMessageId;
        this.updatedAt = now;
    }
}
