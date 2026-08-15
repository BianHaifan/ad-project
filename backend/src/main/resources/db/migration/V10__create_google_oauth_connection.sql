-- V10: secure storage for the recruiter Google OAuth connection slice.
-- Tokens and the PKCE verifier are encrypted at rest (AES-GCM) by the
-- application before they reach these columns; the OAuth client secret and
-- the encryption key live only in local environment configuration and are
-- never stored here.

CREATE TABLE google_recruiter_connections (
    id CHAR(36) NOT NULL,
    recruiter_id CHAR(36) NOT NULL,
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT NOT NULL,
    access_token_expires_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_connections_recruiter UNIQUE (recruiter_id),
    CONSTRAINT fk_google_connections_recruiter FOREIGN KEY (recruiter_id) REFERENCES users (id),
    CONSTRAINT chk_google_connections_version CHECK (version >= 1)
);

CREATE TABLE google_oauth_states (
    id CHAR(36) NOT NULL,
    state_hash CHAR(64) NOT NULL,
    recruiter_id CHAR(36) NOT NULL,
    pkce_verifier_encrypted TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_oauth_states_hash UNIQUE (state_hash),
    CONSTRAINT fk_google_oauth_states_recruiter FOREIGN KEY (recruiter_id) REFERENCES users (id)
);

-- Supports a future cleanup sweep for expired, never-consumed states.
CREATE INDEX idx_google_oauth_states_expires ON google_oauth_states (expires_at);
