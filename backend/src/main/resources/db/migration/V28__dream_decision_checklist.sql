-- FR-55: BR-44's Gate A — an eight-item decision-prudence checklist on
-- Dream, plus a one-time gate-cleared stamp. All additive and nullable.
ALTER TABLE dreams ADD COLUMN decision_skipped_research BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_assumed_no_change BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_trusted_unverified_claim BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_judged_by_appearance BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_under_time_pressure BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_no_outside_input BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_chased_easy_reward BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_dismissed_disagreeing_advice BOOLEAN;
ALTER TABLE dreams ADD COLUMN decision_gate_cleared_at TIMESTAMP;
