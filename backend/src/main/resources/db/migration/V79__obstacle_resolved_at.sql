-- V19 phase 1 (BRD 19.0.0, RM-FR-08): when a blocker was closed, so the
-- Management Report can count "blockers closed" in a reporting period.
-- Set when an obstacle moves to Resolved or Accepted, cleared when it is
-- reopened. Rows already closed are back-filled with their last update, the
-- closest date we have; the report notes this for periods before the release.
--
-- Rollback:
--   ALTER TABLE obstacles DROP COLUMN resolved_at;
ALTER TABLE obstacles ADD COLUMN resolved_at TIMESTAMP;

UPDATE obstacles SET resolved_at = updated_at WHERE status IN ('RESOLVED', 'ACCEPTED');
