package com.adproject.conversation.infrastructure;

import com.adproject.conversation.domain.SenderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class MessageEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "conversation_id", nullable = false, length = 36, columnDefinition = "char(36)") private String conversationId;
    @Column(name = "sender_id", nullable = false, length = 36, columnDefinition = "char(36)") private String senderId;
    @Enumerated(EnumType.STRING) @Column(name = "sender_type", nullable = false, length = 32) private SenderType senderType;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "sent_at", nullable = false) private Instant sentAt;
    @Column(name = "client_message_id", nullable = false, length = 36, columnDefinition = "char(36)") private String clientMessageId;
    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)") private String idempotencyKey;
    @Column(name = "payload_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String payloadHash;

    protected MessageEntity() {}

    public MessageEntity(String id, String conversationId, String senderId, SenderType senderType, String body,
                         Instant sentAt, String clientMessageId, String idempotencyKey, String payloadHash) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.body = body;
        this.sentAt = sentAt;
        this.clientMessageId = clientMessageId;
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
    }

    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public SenderType getSenderType() { return senderType; }
    public String getBody() { return body; }
    public Instant getSentAt() { return sentAt; }
    public String getClientMessageId() { return clientMessageId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadHash() { return payloadHash; }
}
