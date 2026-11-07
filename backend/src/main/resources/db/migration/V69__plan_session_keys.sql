-- V11 Phase 8 (PB-FR-36): every AI request made in a Build my plan session
-- carries that session's key, so requests and cost per finished plan are
-- exact and can be split by feature. The key outlives the draft: it is kept
-- on the finished plan. Empty for AI use outside a session.
ALTER TABLE plan_builder_drafts ADD COLUMN session_key VARCHAR(36);
ALTER TABLE ai_usage_logs ADD COLUMN plan_session_key VARCHAR(36);
ALTER TABLE plan_builder_finished_plans ADD COLUMN session_key VARCHAR(36);

CREATE INDEX idx_ai_usage_logs_plan_session ON ai_usage_logs (plan_session_key);
CREATE INDEX idx_plan_builder_finished_plans_session ON plan_builder_finished_plans (session_key);
