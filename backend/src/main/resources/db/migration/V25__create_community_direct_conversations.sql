CREATE TABLE community_direct_conversations (
    id CHAR(36) NOT NULL,
    participant_a_id CHAR(36) NOT NULL,
    participant_b_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_direct_participants UNIQUE (participant_a_id, participant_b_id),
    CONSTRAINT fk_community_direct_a FOREIGN KEY (participant_a_id) REFERENCES users (id),
    CONSTRAINT fk_community_direct_b FOREIGN KEY (participant_b_id) REFERENCES users (id),
    CONSTRAINT chk_community_direct_order CHECK (participant_a_id < participant_b_id)
);
CREATE INDEX idx_community_direct_a_updated ON community_direct_conversations (participant_a_id, updated_at);
CREATE INDEX idx_community_direct_b_updated ON community_direct_conversations (participant_b_id, updated_at);

CREATE TABLE community_direct_messages (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    sender_id CHAR(36) NOT NULL,
    body TEXT NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_direct_messages_conversation FOREIGN KEY (conversation_id) REFERENCES community_direct_conversations (id),
    CONSTRAINT fk_community_direct_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT chk_community_direct_messages_body CHECK (CHAR_LENGTH(body) BETWEEN 1 AND 2000)
);
CREATE INDEX idx_community_direct_messages_page ON community_direct_messages (conversation_id, sent_at, id);
