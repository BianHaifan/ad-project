-- V9: add third-party meeting provider state to interviews.
-- Existing manual interviews keep their location_or_meeting_url unchanged and
-- gain the safe defaults MANUAL + NOT_APPLICABLE. No OAuth token is stored here;
-- meeting_event_id / meeting_correlation_id are the only external identifiers.

ALTER TABLE interviews
    ADD COLUMN meeting_provider VARCHAR(32) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE interviews
    ADD COLUMN meeting_sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';

ALTER TABLE interviews
    ADD COLUMN meeting_event_id VARCHAR(255) NULL;

ALTER TABLE interviews
    ADD COLUMN meeting_sync_error VARCHAR(1000) NULL;

ALTER TABLE interviews
    ADD COLUMN meeting_correlation_id VARCHAR(100) NULL;

ALTER TABLE interviews
    ADD CONSTRAINT chk_interviews_meeting_provider
        CHECK (meeting_provider IN ('MANUAL', 'GOOGLE_MEET'));

ALTER TABLE interviews
    ADD CONSTRAINT chk_interviews_meeting_sync
        CHECK (meeting_sync_status IN ('NOT_APPLICABLE', 'PENDING', 'READY', 'FAILED'));

-- Provider/status pairing: MANUAL is always NOT_APPLICABLE; GOOGLE_MEET must be in
-- one of the provisioning states (PENDING/READY/FAILED). This prevents a
-- half-finished interview from being recorded with an impossible combination.
ALTER TABLE interviews
    ADD CONSTRAINT chk_interviews_meeting_provider_sync
        CHECK ((meeting_provider = 'MANUAL' AND meeting_sync_status = 'NOT_APPLICABLE')
            OR (meeting_provider = 'GOOGLE_MEET'
                AND meeting_sync_status IN ('PENDING', 'READY', 'FAILED')));

-- At most one external calendar event maps to a single interview, so a retry that
-- reuses the same Google event id cannot create a duplicate external meeting.
CREATE UNIQUE INDEX uk_interviews_meeting_event ON interviews (meeting_event_id);

-- A non-null correlation key identifies exactly one provisioning attempt. Many rows
-- may hold NULL, but a retry cannot mint a second interview for the same key.
CREATE UNIQUE INDEX uk_interviews_meeting_correlation ON interviews (meeting_correlation_id);
