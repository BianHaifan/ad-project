CREATE TABLE resume_snapshots (
    id CHAR(36) NOT NULL,
    resume_id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    location VARCHAR(100) NOT NULL,
    headline VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    experiences_json TEXT NOT NULL,
    resume_version INT NOT NULL,
    resume_created_at DATETIME(6) NOT NULL,
    resume_updated_at DATETIME(6) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_snapshots_resume FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT fk_resume_snapshots_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT chk_resume_snapshots_age CHECK (age BETWEEN 16 AND 100),
    CONSTRAINT chk_resume_snapshots_version CHECK (resume_version >= 1)
);

CREATE TABLE applications (
    id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    resume_id CHAR(36) NOT NULL,
    resume_snapshot_id CHAR(36) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    share_profile BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_applications_job_candidate UNIQUE (job_id, candidate_id),
    CONSTRAINT uk_applications_snapshot UNIQUE (resume_snapshot_id),
    CONSTRAINT fk_applications_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT fk_applications_resume FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT fk_applications_snapshot FOREIGN KEY (resume_snapshot_id) REFERENCES resume_snapshots (id),
    CONSTRAINT chk_applications_version CHECK (version >= 1)
);

CREATE TABLE application_status_events (
    id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NULL,
    request_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_application_events_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_application_events_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_application_events_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE TABLE idempotency_records (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    application_id CHAR(36) NOT NULL,
    http_status INT NOT NULL,
    response_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_scope UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_idempotency_application FOREIGN KEY (application_id) REFERENCES applications (id)
);

CREATE INDEX idx_resume_snapshots_candidate ON resume_snapshots (candidate_id, captured_at);
CREATE INDEX idx_applications_candidate_applied ON applications (candidate_id, applied_at);
CREATE INDEX idx_applications_job_status ON applications (job_id, status);
CREATE INDEX idx_application_events_application ON application_status_events (application_id, occurred_at, id);
