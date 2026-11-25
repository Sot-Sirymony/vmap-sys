-- V32 phase 3 (BRD 32 D-32-7, D-32-8, SF-FR-08 to SF-FR-10): Draft all stages.
-- The stage that was current when a set was made. A set drafted for a later
-- stage (source DRAFT_ALL) is shown as "drafted before you finished the earlier
-- stages" once the user reaches it.
ALTER TABLE streamline_suggestion_sets ADD COLUMN drafted_at_stage INT;
UPDATE streamline_suggestion_sets SET drafted_at_stage = stage WHERE drafted_at_stage IS NULL;
