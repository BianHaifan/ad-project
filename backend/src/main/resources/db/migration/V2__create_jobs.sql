CREATE TABLE jobs (
    id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    created_by CHAR(36) NOT NULL,
    owner_id CHAR(36) NULL,
    title VARCHAR(200) NOT NULL,
    employment_type VARCHAR(32) NOT NULL,
    workplace_type VARCHAR(32) NOT NULL,
    location VARCHAR(100) NOT NULL,
    salary_min BIGINT NOT NULL,
    salary_max BIGINT NOT NULL,
    salary_currency CHAR(3) NOT NULL,
    salary_period VARCHAR(16) NOT NULL,
    description TEXT NOT NULL,
    requirements_json TEXT NOT NULL,
    skills_json TEXT NOT NULL,
    deadline DATETIME(6) NULL,
    visibility VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    applicant_count INT NOT NULL DEFAULT 0,
    published_at DATETIME(6) NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_jobs_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_jobs_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_jobs_salary_non_negative CHECK (salary_min >= 0 AND salary_max >= salary_min),
    CONSTRAINT chk_jobs_applicant_count CHECK (applicant_count >= 0),
    CONSTRAINT chk_jobs_version CHECK (version >= 1)
);

CREATE INDEX idx_jobs_company_status ON jobs (company_id, status);
CREATE INDEX idx_jobs_company_created_id ON jobs (company_id, created_at, id);
CREATE INDEX idx_jobs_company_owner ON jobs (company_id, owner_id);
