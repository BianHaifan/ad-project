-- Keep existing application conversations intact.  A null application_id is reserved for
-- a recruiter contacting a candidate surfaced by a completed Agent screening run.
ALTER TABLE conversations
    MODIFY application_id CHAR(36) NULL,
    ADD COLUMN conversation_type VARCHAR(32) NOT NULL DEFAULT 'APPLICATION' AFTER id,
    ADD COLUMN initiator_recruiter_id CHAR(36) NULL AFTER company_id,
    ADD CONSTRAINT fk_conversations_initiator_recruiter
        FOREIGN KEY (initiator_recruiter_id) REFERENCES users (id);

CREATE UNIQUE INDEX uk_conversations_outreach
    ON conversations (conversation_type, job_id, candidate_id, company_id, initiator_recruiter_id);
