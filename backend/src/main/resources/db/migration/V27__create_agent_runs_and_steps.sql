CREATE TABLE agent_runs (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    instruction VARCHAR(2000) NOT NULL,
    client_context_json TEXT NULL,
    target_type VARCHAR(32) NULL,
    target_id CHAR(36) NULL,
    status VARCHAR(32) NOT NULL,
    confirmation_status VARCHAR(32) NOT NULL,
    preview_json TEXT NULL,
    preview_expires_at DATETIME(6) NULL,
    message VARCHAR(500) NULL,
    error_code VARCHAR(100) NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_runs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_agent_runs_version CHECK (version >= 1)
);

CREATE INDEX idx_agent_runs_user_created ON agent_runs (user_id, created_at, id);
CREATE INDEX idx_agent_runs_status_expiry ON agent_runs (status, preview_expires_at);

CREATE TABLE agent_steps (
    id CHAR(36) NOT NULL,
    run_id CHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    tool_name VARCHAR(100) NULL,
    input_summary VARCHAR(500) NULL,
    output_summary VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(100) NULL,
    duration_ms BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_steps_run_sequence UNIQUE (run_id, sequence_no),
    CONSTRAINT fk_agent_steps_run FOREIGN KEY (run_id) REFERENCES agent_runs (id),
    CONSTRAINT chk_agent_steps_sequence CHECK (sequence_no >= 1),
    CONSTRAINT chk_agent_steps_duration CHECK (duration_ms >= 0)
);

CREATE INDEX idx_agent_steps_run_created ON agent_steps (run_id, sequence_no);
