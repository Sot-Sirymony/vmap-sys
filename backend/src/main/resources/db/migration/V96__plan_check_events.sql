-- V31 MVP (BRD 31.0.0, DC-FR-64, section 7): one row per rule each time the
-- Build my plan check runs, and one per rule when a plan is saved. Counts only,
-- never text (DC-NFR-04). The admin AI page reads these for DCM-01 to DCM-07.
-- user_id is a plain column: a deleted account only loses its link (AccountService).
CREATE TABLE plan_check_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    dream_id BIGINT,
    session_key VARCHAR(36),
    rule VARCHAR(8) NOT NULL,
    must_fails INT NOT NULL DEFAULT 0,
    should_fails INT NOT NULL DEFAULT 0,
    -- The first check of a draft session (DCM-01, DCM-02).
    first_run BOOLEAN NOT NULL DEFAULT FALSE,
    -- Written by Save my plan, after the server re-ran the rules (DCM-03 to DCM-05).
    at_save BOOLEAN NOT NULL DEFAULT FALSE,
    -- Should fails the participant accepted with "I understand" (D-31-4).
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    -- Items checked by this rule, and of them the AI-suggested items the participant has not changed (DCM-06).
    items INT NOT NULL DEFAULT 0,
    ai_items INT NOT NULL DEFAULT 0,
    ai_fails INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_plan_check_events_created ON plan_check_events (created_at);
CREATE INDEX idx_plan_check_events_session ON plan_check_events (session_key);

-- R-07 (DC-FR-61): an AI owner that was not "Me" or a role, removed by the server (DCM-07).
CREATE TABLE ai_owner_strips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    feature VARCHAR(40) NOT NULL,
    owners_seen INT NOT NULL,
    owners_stripped INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ai_owner_strips_created ON ai_owner_strips (created_at);
