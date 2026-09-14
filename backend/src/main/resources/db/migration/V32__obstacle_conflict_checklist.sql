-- FR-62: a four-item conduct checklist for PARTNER-type conflict
-- obstacles (BR-51). All additive and nullable — null means "unanswered."
ALTER TABLE obstacles ADD COLUMN conflict_no_character_attacks BOOLEAN;
ALTER TABLE obstacles ADD COLUMN conflict_stayed_on_incident BOOLEAN;
ALTER TABLE obstacles ADD COLUMN conflict_no_threats_or_sarcasm BOOLEAN;
ALTER TABLE obstacles ADD COLUMN conflict_defined_win_win BOOLEAN;
