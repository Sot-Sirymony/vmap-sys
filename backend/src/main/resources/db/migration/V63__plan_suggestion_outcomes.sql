-- V11 Phase 1 (PB-FR-35): how many items each plan builder answer suggested,
-- and how many the participant added as they were or after editing. The rest
-- were discarded. Counts only: no text of any suggestion is stored.
--
-- suggested_items stays null for features that are not plan builders.
ALTER TABLE ai_usage_logs ADD COLUMN suggested_items INTEGER;
ALTER TABLE ai_usage_logs ADD COLUMN added_items INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ai_usage_logs ADD COLUMN edited_items INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_ai_usage_logs_feature_created ON ai_usage_logs (feature, created_at);
