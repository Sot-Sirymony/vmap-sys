-- V32 phase 1 (BRD 32 D-32-1 to D-32-3, SF-FR-01 to SF-FR-04).
--
-- The last AI suggestions for one review and stage, so reopening the stage
-- shows them at once without a new request. fingerprint is the SHA-256 of the
-- stage and the review's active items; when it no longer matches, the set is
-- dropped and a new one is asked for. cards is the checked suggestion list as
-- JSON: codes, titles, actions and short texts, never names of people (D-32-2).
-- Rows go when the review is completed or abandoned, and with the account.
CREATE TABLE streamline_suggestion_sets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    review_id BIGINT NOT NULL REFERENCES streamline_reviews(id) ON DELETE CASCADE,
    stage INT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    -- STAGE (one stage asked) or DRAFT_ALL (BRD 32 phase 3).
    source VARCHAR(20) NOT NULL,
    model VARCHAR(60),
    -- The usage-log row of the request, so a saved set can still be rated.
    request_id BIGINT,
    cards TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_streamline_suggestion_sets_stage UNIQUE (review_id, stage),
    CONSTRAINT ck_streamline_suggestion_sets_stage CHECK (stage BETWEEN 1 AND 5),
    CONSTRAINT ck_streamline_suggestion_sets_source CHECK (source IN ('STAGE', 'DRAFT_ALL'))
);
CREATE INDEX idx_streamline_suggestion_sets_user ON streamline_suggestion_sets (user_id);

-- D-32-3: the model for Streamline suggestions and picks; empty keeps today's rule.
ALTER TABLE ai_settings ADD COLUMN streamline_model VARCHAR(60);
