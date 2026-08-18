ALTER TABLE users ADD COLUMN auth_version INT NOT NULL DEFAULT 1;

CREATE TABLE password_reset_codes (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempt_count INT NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_codes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_password_reset_user_created ON password_reset_codes (user_id, created_at);
CREATE INDEX idx_password_reset_expiry ON password_reset_codes (expires_at);
