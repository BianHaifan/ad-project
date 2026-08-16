package com.adproject.conversation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "message_attachments")
public class MessageAttachmentEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "message_id", nullable = false, length = 36, columnDefinition = "char(36)") private String messageId;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "content_type", nullable = false, length = 127) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @JdbcTypeCode(Types.BLOB) @Column(nullable = false, columnDefinition = "LONGBLOB") private byte[] content;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MessageAttachmentEntity() {}

    public MessageAttachmentEntity(String id, String messageId, String fileName, String contentType,
                                   long sizeBytes, byte[] content, Instant createdAt) {
        this.id = id;
        this.messageId = messageId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
