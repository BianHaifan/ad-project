package com.adproject.conversation.api;

import java.time.Instant;
import java.util.List;

public final class ConversationDtos {
    private ConversationDtos() {}

    public record Company(String companyId, String name, String logoUrl, String stage, String employeeRange,
                          String verificationStatus, String website, String description, String location,
                          int version, Instant createdAt, Instant updatedAt) {}

    public record Participant(String userId, String fullName, String avatarUrl, String title, Company company,
                              boolean online) {}

    public record Message(String messageId, String conversationId, String body, String senderType, Instant sentAt,
                          String clientMessageId, String deliveryStatus) {}

    public record Summary(String conversationId, String applicationId, String jobId, Instant createdAt,
                          Instant updatedAt, Participant participant, Message lastMessage, long unreadCount,
                          String jobTitle) {}

    public record Detail(String conversationId, String applicationId, String jobId, Instant createdAt,
                         Instant updatedAt, Participant participant, Object context) {}

    public record PageMeta(int page, int pageSize, long total, boolean hasNext) {}
    public record ListResponse(List<Summary> data, PageMeta meta) {}
    public record DetailResponse(Detail data) {}

    public record MessageListMeta(String nextCursor, boolean hasMore) {}
    public record MessageListResponse(List<Message> data, MessageListMeta meta) {}
    public record MessageResponse(Message data) {}

    public record SendMessageRequest(String body, String clientMessageId) {}
    public record ReadStateRequest(String lastReadMessageId) {}
}
