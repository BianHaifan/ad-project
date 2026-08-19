ALTER TABLE agent_runs ADD COLUMN confirmation_id CHAR(36) NULL;
ALTER TABLE agent_runs ADD COLUMN execution_idempotency_key CHAR(36) NULL;
ALTER TABLE agent_runs ADD COLUMN confirmed_at DATETIME(6) NULL;
ALTER TABLE agent_runs ADD COLUMN completed_at DATETIME(6) NULL;
ALTER TABLE agent_runs ADD COLUMN result_json TEXT NULL;

ALTER TABLE agent_runs
    ADD CONSTRAINT uk_agent_runs_confirmation UNIQUE (confirmation_id);
ALTER TABLE agent_runs
    ADD CONSTRAINT uk_agent_runs_user_execution_key UNIQUE (user_id, execution_idempotency_key);
