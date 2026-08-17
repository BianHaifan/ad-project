-- V14: per-user avatar storage. One row per user (PRIMARY KEY on user_id),
-- so a user has at most one avatar. Only PNG/JPEG are ever written, and the
-- content type is controlled server-side; users.avatar_url is updated to the
-- stable API path /api/v1/avatars/{userId} by the avatar service.
CREATE TABLE user_avatars (
    user_id CHAR(36) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_avatars_content_type CHECK (content_type IN ('image/png', 'image/jpeg'))
);
