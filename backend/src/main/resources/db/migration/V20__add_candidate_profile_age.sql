-- V16: candidate_profiles gains a self-editable age so Profile owns identity fields.
-- resumes.age is kept (NOT NULL) but is now synced from here at save time.

ALTER TABLE candidate_profiles
    ADD COLUMN age INT NULL;

ALTER TABLE candidate_profiles
    ADD CONSTRAINT chk_candidate_profiles_age
        CHECK (age IS NULL OR age BETWEEN 16 AND 100);
