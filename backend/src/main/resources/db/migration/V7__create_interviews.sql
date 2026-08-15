CREATE TABLE interviews (
    id CHAR(36) NOT NULL,
    application_id CHAR(36) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    duration_minutes INT NOT NULL,
    mode VARCHAR(32) NOT NULL,
    location_or_meeting_url VARCHAR(1000) NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_interviews_application UNIQUE (application_id),
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT chk_interviews_duration CHECK (duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT chk_interviews_version CHECK (version >= 1)
);
