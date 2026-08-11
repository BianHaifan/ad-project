CREATE TABLE job_audit_events (
    id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_audit_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_job_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_job_audit_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_job_audit_job_occurred ON job_audit_events (job_id, occurred_at, id);
CREATE INDEX idx_job_audit_company_occurred ON job_audit_events (company_id, occurred_at, id);
