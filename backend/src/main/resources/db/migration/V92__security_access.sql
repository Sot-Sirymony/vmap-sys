-- V24 (BRD 24.0.0) phase 1: the sign-in method settings an Admin controls
-- (SA-FR-01) and the method each session was opened with (D-24-3).
--
-- One row (id 1), seeded with today's behaviour: password and Google sign-in on,
-- two-step sign-in required for privileged roles only (V21 D-21-2).
CREATE TABLE security_settings (
    id BIGINT PRIMARY KEY,
    password_sign_in BOOLEAN NOT NULL,
    google_sign_in BOOLEAN NOT NULL,
    two_factor_scope VARCHAR(20) NOT NULL,
    -- The admin who last changed it; empty if that admin's account is later deleted.
    updated_by_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_security_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_security_settings_scope CHECK (two_factor_scope IN ('PRIVILEGED', 'EVERYONE'))
);

INSERT INTO security_settings (id, password_sign_in, google_sign_in, two_factor_scope, created_at, updated_at)
VALUES (1, TRUE, TRUE, 'PRIVILEGED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- PASSWORD or GOOGLE; empty for sessions opened before V24, which no switch ends.
ALTER TABLE auth_sessions ADD COLUMN sign_in_method VARCHAR(20);
