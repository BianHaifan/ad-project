CREATE TABLE users (
    id CHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    accepted_terms_version VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE companies (
    id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    logo_url VARCHAR(500) NULL,
    stage VARCHAR(32) NULL,
    employee_range VARCHAR(50) NULL,
    verification_status VARCHAR(32) NOT NULL,
    website VARCHAR(500) NULL,
    description TEXT NULL,
    location VARCHAR(100) NULL,
    version INT NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_companies_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE company_members (
    id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_company_members_user UNIQUE (user_id),
    CONSTRAINT uk_company_members_company_user UNIQUE (company_id, user_id),
    CONSTRAINT fk_company_members_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_company_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_token_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_replacement FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (expires_at);
