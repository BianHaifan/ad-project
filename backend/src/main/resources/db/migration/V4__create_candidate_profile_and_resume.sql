CREATE TABLE candidate_profiles (
    user_id CHAR(36) NOT NULL,
    headline VARCHAR(200) NOT NULL,
    location VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_candidate_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_candidate_profiles_version CHECK (version >= 1)
);

CREATE TABLE resumes (
    id CHAR(36) NOT NULL,
    candidate_id CHAR(36) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    location VARCHAR(100) NOT NULL,
    headline VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    experiences_json TEXT NOT NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_resumes_candidate UNIQUE (candidate_id),
    CONSTRAINT fk_resumes_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT chk_resumes_age CHECK (age BETWEEN 16 AND 100),
    CONSTRAINT chk_resumes_version CHECK (version >= 1)
);

