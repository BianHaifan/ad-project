-- Relax the message body constraint so an attachment-only message (no text) is valid.
ALTER TABLE messages DROP CONSTRAINT chk_messages_body;
ALTER TABLE messages ADD CONSTRAINT chk_messages_body CHECK (LENGTH(body) BETWEEN 0 AND 5000);

CREATE TABLE message_attachments (
    id CHAR(36) NOT NULL,
    message_id CHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_message_attachments_message UNIQUE (message_id),
    CONSTRAINT fk_message_attachments_message FOREIGN KEY (message_id) REFERENCES messages (id)
);
