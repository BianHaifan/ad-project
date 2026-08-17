-- V15: extend candidate_profiles with optional self-editable contact fields.
-- gender is a controlled enum; phone and birthplace are free-form but bounded.
-- location is intentionally left untouched (legacy field, no longer edited by the Me UI).

ALTER TABLE candidate_profiles
    ADD COLUMN gender VARCHAR(32) NULL;

ALTER TABLE candidate_profiles
    ADD COLUMN phone VARCHAR(32) NULL;

ALTER TABLE candidate_profiles
    ADD COLUMN birthplace VARCHAR(100) NULL;

ALTER TABLE candidate_profiles
    ADD CONSTRAINT chk_candidate_profiles_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'));
