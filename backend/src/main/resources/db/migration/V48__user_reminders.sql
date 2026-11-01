-- V8 FR-77: reminders are opt-in. The time zone comes from the user's browser,
-- and last_reminder_on keeps a restart from sending the same day twice.
ALTER TABLE app_users ADD COLUMN reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN reminder_time_zone VARCHAR(60);
ALTER TABLE app_users ADD COLUMN last_reminder_on DATE;
