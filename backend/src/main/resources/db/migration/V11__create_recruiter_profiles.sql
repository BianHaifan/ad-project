-- V11: recruiter-specific editable profile fields.
-- Users.avatar_url and users.full_name remain the shared account fields.
-- The company relationship stays in company_members and is read-only for this profile.
CREATE TABLE recruiter_profiles (
    user_id CHAR(36) NOT NULL,
    title VARCHAR(100) NOT NULL,
    bio VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_recruiter_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);
