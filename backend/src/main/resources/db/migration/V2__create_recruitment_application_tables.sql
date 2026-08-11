CREATE TABLE jobs (
    id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    created_by CHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    employment_type VARCHAR(32) NOT NULL,
    workplace_type VARCHAR(32) NOT NULL,
    location VARCHAR(100) NOT NULL,
    salary_min INT NOT NULL,
    salary_max INT NOT NULL,
    salary_currency CHAR(3) NOT NULL,
    salary_period VARCHAR(16) NOT NULL,
    description TEXT NOT NULL,
    requirements_json JSON NOT NULL,
    skills_json JSON NOT NULL,
    deadline DATETIME(6) NULL,
    visibility VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    published_at DATETIME(6) NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_jobs_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX idx_jobs_status_visibility ON jobs (status, visibility, published_at);
CREATE INDEX idx_jobs_company_status ON jobs (company_id, status);

CREATE TABLE resumes (
    id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    location VARCHAR(100) NOT NULL,
    headline VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    experiences_json JSON NOT NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_resumes_candidate UNIQUE (candidate_id),
    CONSTRAINT fk_resumes_candidate FOREIGN KEY (candidate_id) REFERENCES users (id)
);

CREATE TABLE resume_snapshots (
    id CHAR(36) NOT NULL,
    source_resume_id CHAR(36) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    location VARCHAR(100) NOT NULL,
    headline VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    experiences_json JSON NOT NULL,
    resume_version INT NOT NULL,
    resume_created_at DATETIME(6) NOT NULL,
    resume_updated_at DATETIME(6) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_snapshots_source FOREIGN KEY (source_resume_id) REFERENCES resumes (id)
);

CREATE TABLE applications (
    id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    resume_snapshot_id CHAR(36) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    share_profile BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_applications_candidate_job UNIQUE (candidate_id, job_id),
    CONSTRAINT uk_applications_snapshot UNIQUE (resume_snapshot_id),
    CONSTRAINT fk_applications_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT fk_applications_snapshot FOREIGN KEY (resume_snapshot_id) REFERENCES resume_snapshots (id)
);

CREATE INDEX idx_applications_job_status ON applications (job_id, status);
CREATE INDEX idx_applications_candidate_applied ON applications (candidate_id, applied_at);

CREATE TABLE application_status_events (
    id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NOT NULL,
    company_id CHAR(36) NULL,
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

CREATE INDEX idx_application_events_timeline ON application_status_events (application_id, occurred_at);

CREATE TABLE idempotency_records (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    operation_name VARCHAR(100) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_scope UNIQUE (user_id, operation_name, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id)
);
