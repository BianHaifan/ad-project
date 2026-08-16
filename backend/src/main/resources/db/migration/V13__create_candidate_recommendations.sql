ALTER TABLE resumes ADD COLUMN skills_json TEXT NULL;
UPDATE resumes SET skills_json = '[]' WHERE skills_json IS NULL;
ALTER TABLE resumes MODIFY skills_json TEXT NOT NULL;

ALTER TABLE resume_snapshots ADD COLUMN skills_json TEXT NULL;
UPDATE resume_snapshots SET skills_json = '[]' WHERE skills_json IS NULL;
ALTER TABLE resume_snapshots MODIFY skills_json TEXT NOT NULL;

CREATE TABLE candidate_job_preferences (
    candidate_id CHAR(36) NOT NULL,
    desired_titles_json TEXT NOT NULL,
    preferred_locations_json TEXT NOT NULL,
    workplace_types_json TEXT NOT NULL,
    employment_types_json TEXT NOT NULL,
    minimum_salary BIGINT NULL,
    salary_currency CHAR(3) NOT NULL,
    salary_period VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (candidate_id),
    CONSTRAINT fk_candidate_job_preferences_user FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT chk_candidate_job_preferences_salary CHECK (minimum_salary IS NULL OR minimum_salary >= 0),
    CONSTRAINT chk_candidate_job_preferences_version CHECK (version >= 1)
);

CREATE TABLE candidate_job_recommendations (
    id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    job_id CHAR(36) NOT NULL,
    score INT NOT NULL,
    source VARCHAR(16) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    feature_version VARCHAR(100) NOT NULL,
    strong_matches_json TEXT NOT NULL,
    gaps_json TEXT NOT NULL,
    evidence_json TEXT NOT NULL,
    resume_version INT NOT NULL,
    preference_version INT NOT NULL,
    job_version INT NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_candidate_job_recommendations_pair UNIQUE (candidate_id, job_id),
    CONSTRAINT fk_candidate_job_recommendations_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT fk_candidate_job_recommendations_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT chk_candidate_job_recommendations_score CHECK (score BETWEEN 0 AND 100)
);

CREATE INDEX idx_candidate_job_recommendations_rank
    ON candidate_job_recommendations (candidate_id, score, generated_at);
