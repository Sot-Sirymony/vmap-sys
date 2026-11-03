-- V9 Phase 6 (AI-FR-15): reminder emails may carry a short AI encouragement
-- that quotes the participant's own passion statement. Off by default: it runs
-- every day without anyone present and counts toward the monthly budget.
ALTER TABLE ai_settings ADD COLUMN ai_reminders BOOLEAN NOT NULL DEFAULT FALSE;
