-- V21 (BRD 21.0.0, TF-FR-03, TF-FR-06): an authenticator being set up, or a
-- switch from the email method, waits here until a code from the app is
-- confirmed, so the settings in use never change half way.
ALTER TABLE two_factor_settings ADD COLUMN pending_totp_secret VARCHAR(512);
ALTER TABLE two_factor_settings ADD COLUMN pending_created_at TIMESTAMP;
