-- V32 phase 2 (BRD 32 D-32-4 to D-32-6, SF-FR-05 to SF-FR-07).
--
-- Apply selected / Apply all run on the server as one batch. Before each
-- action the server records what it is about to change (before_state, JSON),
-- so the whole batch can be undone in one step while its stage is current and
-- for 24 hours. The *_updated_at columns hold the changed records' update
-- times at the end of the batch: a record changed again after that is left as
-- it is by undo (BR-32-6).
CREATE TABLE streamline_apply_batches (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    review_id BIGINT NOT NULL REFERENCES streamline_reviews(id) ON DELETE CASCADE,
    stage INT NOT NULL,
    total INT NOT NULL,
    applied_count INT NOT NULL,
    -- The position (1-based) of the action that failed, or empty when all applied.
    failed_at_position INT,
    error VARCHAR(500),
    undone_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_streamline_apply_batches_stage CHECK (stage BETWEEN 1 AND 5)
);
CREATE INDEX idx_streamline_apply_batches_review ON streamline_apply_batches (review_id, stage);
CREATE INDEX idx_streamline_apply_batches_user ON streamline_apply_batches (user_id);

CREATE TABLE streamline_apply_batch_entries (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES streamline_apply_batches(id) ON DELETE CASCADE,
    position INT NOT NULL,
    -- UPDATE, DELETE, MERGE, SIMPLIFY or REPEAT.
    action VARCHAR(20) NOT NULL,
    item_id BIGINT NOT NULL REFERENCES streamline_items(id) ON DELETE CASCADE,
    before_state TEXT NOT NULL,
    item_updated_at TIMESTAMP WITH TIME ZONE,
    target_updated_at TIMESTAMP WITH TIME ZONE,
    -- For a merge: the item merged into.
    other_updated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_streamline_apply_batch_entries_action CHECK (action IN ('UPDATE', 'DELETE', 'MERGE', 'SIMPLIFY', 'REPEAT'))
);
CREATE INDEX idx_streamline_apply_batch_entries_batch ON streamline_apply_batch_entries (batch_id);
