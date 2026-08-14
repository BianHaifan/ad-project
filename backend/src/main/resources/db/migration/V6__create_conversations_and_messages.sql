CREATE TABLE conversations (
    id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    last_message_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversations_application UNIQUE (application_id),
    CONSTRAINT fk_conversations_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_conversations_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_conversations_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_conversations_candidate ON conversations (candidate_id, updated_at);
CREATE INDEX idx_conversations_company ON conversations (company_id, updated_at);

CREATE TABLE messages (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    sender_id CHAR(36) NOT NULL,
    sender_type VARCHAR(32) NOT NULL,
    body TEXT NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    client_message_id CHAR(36) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_messages_conversation_client UNIQUE (conversation_id, client_message_id),
    CONSTRAINT uk_messages_sender_idempotency UNIQUE (sender_id, idempotency_key),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT chk_messages_body CHECK (LENGTH(body) BETWEEN 1 AND 5000)
);

CREATE INDEX idx_messages_conversation_sent ON messages (conversation_id, sent_at, id);

CREATE TABLE conversation_read_states (
    conversation_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    last_read_message_id CHAR(36) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_read_states_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_read_states_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_read_states_message FOREIGN KEY (last_read_message_id) REFERENCES messages (id)
);
