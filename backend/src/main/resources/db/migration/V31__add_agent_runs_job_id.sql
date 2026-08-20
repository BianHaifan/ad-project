-- Job context for recruiter agent runs (screen_applicants and interview tools).
ALTER TABLE agent_runs ADD COLUMN job_id CHAR(36) NULL AFTER conversation_id;
