-- V32 phase 4 (BRD 32 D-32-9 to D-32-12, SF-FR-11 to SF-FR-15).

-- D-32-9: how often a REMIND item reminds: DAILY, WEEKLY on remind_day (1 = Monday
-- to 7 = Sunday) or MONTHLY on remind_day (1 to 28).
ALTER TABLE streamline_items ADD COLUMN remind_cadence VARCHAR(10);
ALTER TABLE streamline_items ADD COLUMN remind_day INT;
ALTER TABLE streamline_items ADD CONSTRAINT ck_streamline_items_remind_cadence
    CHECK (remind_cadence IS NULL OR remind_cadence IN ('DAILY', 'WEEKLY', 'MONTHLY'));

-- D-32-11: the weekly Streamline nudge, on by default.
ALTER TABLE app_users ADD COLUMN streamline_nudges_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- D-32-11, BR-32-10: nudges sent, for "one a week" and "the same goal or step once every 30 days".
CREATE TABLE streamline_nudges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    anchor_type VARCHAR(4) NOT NULL,
    anchor_id BIGINT NOT NULL,
    rule VARCHAR(30) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_streamline_nudges_anchor CHECK (anchor_type IN ('GOAL', 'STEP'))
);
CREATE INDEX idx_streamline_nudges_user ON streamline_nudges (user_id, sent_at);

-- D-32-12: dismissed suggestions, on every device. rule is the candidate's rule, or AI_PICK.
CREATE TABLE streamline_candidate_dismissals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    anchor_type VARCHAR(4) NOT NULL,
    anchor_id BIGINT NOT NULL,
    rule VARCHAR(30) NOT NULL,
    dismissed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_streamline_candidate_dismissals UNIQUE (user_id, anchor_type, anchor_id, rule),
    CONSTRAINT ck_streamline_candidate_dismissals_anchor CHECK (anchor_type IN ('GOAL', 'STEP'))
);
