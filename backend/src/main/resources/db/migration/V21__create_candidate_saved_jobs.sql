CREATE TABLE candidate_saved_jobs (
    id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_candidate_saved_jobs_pair UNIQUE (candidate_id, job_id),
    CONSTRAINT fk_candidate_saved_jobs_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT fk_candidate_saved_jobs_job FOREIGN KEY (job_id) REFERENCES jobs (id)
);

CREATE INDEX idx_candidate_saved_jobs_candidate_created
    ON candidate_saved_jobs (candidate_id, created_at);
