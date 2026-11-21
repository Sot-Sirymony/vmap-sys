-- V19 (BRD 19.0.0, RM-FR-49): an optional reminder to prepare a report,
-- weekly on a chosen weekday (1 = Monday ... 7 = Sunday) or monthly on the
-- last working day. The report_coach_shares table the BRD lists for this
-- migration waits for decision D-19-3 (RM-FR-50) and is not created here.
ALTER TABLE app_users ADD COLUMN report_reminder VARCHAR(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE app_users ADD COLUMN report_reminder_day INTEGER;
