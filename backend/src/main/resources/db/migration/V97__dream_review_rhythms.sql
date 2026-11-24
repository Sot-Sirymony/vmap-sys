-- V31 v1 step 3 (BRD 31 stage 8, DC-FR-48 to DC-FR-50, D-31-9): how often the
-- participant reviews one dream. One row per dream; no Review rows are made
-- ahead of time. The Reviews page and the daily reminder read it to say which
-- reviews are due.
CREATE TABLE dream_review_rhythms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    dream_id BIGINT NOT NULL REFERENCES dreams(id) ON DELETE CASCADE,
    daily BOOLEAN NOT NULL DEFAULT FALSE,
    -- MON to SUN, or empty for no weekly review.
    weekly_day VARCHAR(3),
    -- 1 to 28, or empty for no monthly review.
    monthly_day INT,
    quarterly BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_dream_review_rhythms_dream UNIQUE (dream_id),
    CONSTRAINT ck_dream_review_rhythms_weekly CHECK (weekly_day IS NULL OR weekly_day IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    CONSTRAINT ck_dream_review_rhythms_monthly CHECK (monthly_day IS NULL OR monthly_day BETWEEN 1 AND 28)
);
CREATE INDEX idx_dream_review_rhythms_user ON dream_review_rhythms (user_id);
