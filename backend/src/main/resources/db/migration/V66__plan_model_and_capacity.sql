-- V11 Phase 6.
-- PB-FR-39: an optional model for plan building (goals, steps, tasks, helpers,
-- checks); empty means the main model is used for everything.
ALTER TABLE ai_settings ADD COLUMN plan_model VARCHAR(60);
-- PB-FR-16: the hours a week a participant has for plan work, remembered from
-- their last answer; empty until they give one.
ALTER TABLE app_users ADD COLUMN plan_hours_per_week NUMERIC(5, 1);
