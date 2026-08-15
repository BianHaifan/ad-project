CREATE TABLE interview_audit_events (
    id CHAR(36) NOT NULL,
    interview_id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_value VARCHAR(2000) NULL,
    after_value VARCHAR(2000) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NULL,
    request_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_audit_interview FOREIGN KEY (interview_id) REFERENCES interviews (id),
    CONSTRAINT fk_interview_audit_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_interview_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_interview_audit_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_interview_audit_interview_occurred ON interview_audit_events (interview_id, occurred_at, id);
CREATE INDEX idx_interview_audit_application ON interview_audit_events (application_id);
