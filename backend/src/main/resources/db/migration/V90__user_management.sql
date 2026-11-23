-- V23 (BRD 23.0.0) phase 2: what the Users page shows about each account
-- (UM-FR-17, UM-FR-19). The ids point at the admin who acted; if that admin's
-- account is later deleted the link becomes empty, the security log keeps who.

ALTER TABLE app_users ADD COLUMN created_by_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE app_users ADD COLUMN role_changed_at TIMESTAMP;
ALTER TABLE app_users ADD COLUMN role_changed_by_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE app_users ADD COLUMN blocked_at TIMESTAMP;
ALTER TABLE app_users ADD COLUMN blocked_by_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
-- Set whenever a session opens (password, Google or after two-step sign-in).
ALTER TABLE app_users ADD COLUMN last_sign_in_at TIMESTAMP;
