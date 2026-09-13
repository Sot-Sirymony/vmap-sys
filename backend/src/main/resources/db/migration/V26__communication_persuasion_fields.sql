-- FR-52: four additive persuasion fields on communication_messages, matching
-- the sizing of FR-17's existing structured fields. All optional (FR-52.3).
ALTER TABLE communication_messages ADD COLUMN objections_and_answers VARCHAR(2000);
ALTER TABLE communication_messages ADD COLUMN social_proof VARCHAR(2000);
ALTER TABLE communication_messages ADD COLUMN value_comparison VARCHAR(2000);
ALTER TABLE communication_messages ADD COLUMN call_to_action VARCHAR(2000);
