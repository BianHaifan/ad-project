-- The agent client context was accepted, persisted, and forwarded to the planner,
-- but never read anywhere. Remove the column along with the dead data path.
ALTER TABLE agent_runs DROP COLUMN client_context_json;
