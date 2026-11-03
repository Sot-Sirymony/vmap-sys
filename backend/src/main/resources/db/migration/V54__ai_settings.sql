-- V9 Phase 3 groundwork: AI settings an admin manages in the app (AI-FR-24),
-- including the Claude API key. One row only. The key is stored encrypted
-- (AES-GCM); only its last four characters are ever shown again.
CREATE TABLE ai_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    model VARCHAR(60) NOT NULL,
    monthly_budget_usd NUMERIC(8, 2) NOT NULL,
    api_key_encrypted VARCHAR(1000),
    api_key_last4 VARCHAR(4),
    api_key_updated_at TIMESTAMP WITH TIME ZONE,
    api_key_updated_by BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    last_test_at TIMESTAMP WITH TIME ZONE,
    last_test_ok BOOLEAN,
    last_test_message VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_settings_single_row CHECK (id = 1)
);

-- D-3: $20 a month for all participants.
INSERT INTO ai_settings (id, enabled, model, monthly_budget_usd, created_at, updated_at)
VALUES (1, FALSE, 'claude-opus-5-5', 20.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
