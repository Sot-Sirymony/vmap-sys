-- V27 (BRD 27.0.3): the Streamline review (question, delete, simplify, speed
-- up, automate), the task start time and status history that cycle time needs,
-- and repeating tasks.

-- D-27-7: set the first time a task moves to In progress; never cleared. Tasks
-- that started before this release stay empty and are left out of cycle time.
ALTER TABLE task_items ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;

-- SR-FR-06: every status change, for waiting and blocked time.
CREATE TABLE task_status_changes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES task_items(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_task_status_changes_task ON task_status_changes(task_id, changed_at);
CREATE INDEX idx_task_status_changes_user ON task_status_changes(user_id, changed_at);

-- SR-FR-09: a repeating task rule. The job creates the next task once the
-- current one is due, never in the past, and never twice for one date.
CREATE TABLE task_repeat_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    step_id BIGINT NOT NULL REFERENCES vision_steps(id) ON DELETE CASCADE,
    source_task_id BIGINT REFERENCES task_items(id) ON DELETE SET NULL,
    last_task_id BIGINT REFERENCES task_items(id) ON DELETE SET NULL,
    title VARCHAR(220) NOT NULL,
    cadence VARCHAR(10) NOT NULL,
    next_due DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_task_repeat_rules_cadence CHECK (cadence IN ('DAILY', 'WEEKLY', 'MONTHLY'))
);
CREATE INDEX idx_task_repeat_rules_due ON task_repeat_rules(active, next_due);

-- The rule a task was made from; the unique pair makes the job safe to run twice.
ALTER TABLE task_items ADD COLUMN repeat_rule_id BIGINT REFERENCES task_repeat_rules(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_task_items_repeat_due ON task_items(repeat_rule_id, due_date);

-- SR-FR-01: one review per goal or step; at most one open review per anchor
-- (enforced by the service, since partial unique indexes differ between databases).
CREATE TABLE streamline_reviews (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    goal_id BIGINT REFERENCES goals(id) ON DELETE CASCADE,
    step_id BIGINT REFERENCES vision_steps(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    current_stage INTEGER NOT NULL,
    stage1_state VARCHAR(20) NOT NULL,
    stage2_state VARCHAR(20) NOT NULL,
    stage3_state VARCHAR(20) NOT NULL,
    stage4_state VARCHAR(20) NOT NULL,
    stage5_state VARCHAR(20) NOT NULL,
    stage1_reason VARCHAR(500),
    stage2_reason VARCHAR(500),
    stage3_reason VARCHAR(500),
    stage4_reason VARCHAR(500),
    stage5_reason VARCHAR(500),
    baseline_steps INTEGER NOT NULL,
    baseline_tasks INTEGER NOT NULL,
    baseline_hours NUMERIC(10, 2) NOT NULL,
    result_steps INTEGER,
    result_tasks INTEGER,
    result_hours NUMERIC(10, 2),
    baseline_cycle_days NUMERIC(10, 1),
    target_cycle_days NUMERIC(10, 1),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_streamline_reviews_anchor CHECK ((goal_id IS NULL) <> (step_id IS NULL)),
    CONSTRAINT ck_streamline_reviews_status CHECK (status IN ('OPEN', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_streamline_reviews_stage CHECK (current_stage BETWEEN 1 AND 5)
);
CREATE INDEX idx_streamline_reviews_user ON streamline_reviews(user_id, status);

-- One row per step or task in a review's scope.
CREATE TABLE streamline_items (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES streamline_reviews(id) ON DELETE CASCADE,
    step_id BIGINT REFERENCES vision_steps(id) ON DELETE CASCADE,
    task_id BIGINT REFERENCES task_items(id) ON DELETE CASCADE,
    requirement_owner VARCHAR(120),
    requirement_source VARCHAR(20),
    partner_id BIGINT REFERENCES partners(id) ON DELETE SET NULL,
    why_needed VARCHAR(300),
    verdict VARCHAR(20),
    decision VARCHAR(20),
    merge_into_item_id BIGINT REFERENCES streamline_items(id) ON DELETE SET NULL,
    keep_reason VARCHAR(300),
    simplified BOOLEAN NOT NULL DEFAULT FALSE,
    simplify_note VARCHAR(300),
    speed_levers VARCHAR(200),
    automation VARCHAR(20),
    delegate_partner_id BIGINT REFERENCES partners(id) ON DELETE SET NULL,
    archived_with_parent BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    restored_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_streamline_items_target CHECK ((step_id IS NULL) <> (task_id IS NULL))
);
CREATE INDEX idx_streamline_items_review ON streamline_items(review_id);
