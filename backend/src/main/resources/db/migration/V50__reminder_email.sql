-- FR-77: reminders can go to an address other than the login email.
-- NULL means "use the login email", so existing accounts keep their behaviour.
ALTER TABLE app_users ADD COLUMN reminder_email VARCHAR(255);
