ALTER TABLE agent_runs ADD COLUMN conversation_id CHAR(36) NULL AFTER user_id;

UPDATE agent_runs SET conversation_id = id WHERE conversation_id IS NULL;

ALTER TABLE agent_runs MODIFY COLUMN conversation_id CHAR(36) NOT NULL;

CREATE INDEX idx_agent_runs_conversation_created
    ON agent_runs (conversation_id, user_id, created_at, id);
