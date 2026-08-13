package com.adproject.conversation.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public class ConversationReadStateId implements Serializable {
    private String conversationId;
    private String userId;

    public ConversationReadStateId() {}

    public ConversationReadStateId(String conversationId, String userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    public String getConversationId() { return conversationId; }
    public String getUserId() { return userId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ConversationReadStateId that)) return false;
        return Objects.equals(conversationId, that.conversationId) && Objects.equals(userId, that.userId);
    }

    @Override public int hashCode() { return Objects.hash(conversationId, userId); }
}
