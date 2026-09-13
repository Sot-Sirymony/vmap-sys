-- FR-54: a guided conflict-processing worksheet for PARTNER-type obstacles.
-- All additive, nullable, and diagnostic only (FR-54.4) — none of these
-- gate a status transition the way FR-32's root_cause/creative_alternatives
-- do. conflict_private_note is never referenced by ExcelService (BR-43).
ALTER TABLE obstacles ADD COLUMN conflict_incident VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN conflict_cost VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN conflict_other_perspective VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN conflict_lesson VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN conflict_private_note VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN conflict_next_action VARCHAR(2000);
