-- V17 phase 4 (BRD 17.0.0, BL-FR-28): how many times a task has entered
-- Blocked, so a task blocked a third time can suggest breaking it down or
-- recording an obstacle. Additive: existing tasks start at 0 (BL-NFR-01).
--
-- Rollback: ALTER TABLE task_items DROP COLUMN blocked_count;

ALTER TABLE task_items ADD COLUMN blocked_count INTEGER NOT NULL DEFAULT 0;
