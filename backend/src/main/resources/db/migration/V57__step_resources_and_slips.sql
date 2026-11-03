-- V9 Phase 5. v0.1 FR-05 asks each step for the resources and people it
-- needs, and the AI plan check flags a step without them (AI-FR-09, AC-10).
-- overdue_reschedules counts how often an overdue step was given a new date,
-- so a step that slips twice can get help (AI-FR-11).
ALTER TABLE vision_steps ADD COLUMN resources VARCHAR(1000);
ALTER TABLE vision_steps ADD COLUMN overdue_reschedules INTEGER NOT NULL DEFAULT 0;
