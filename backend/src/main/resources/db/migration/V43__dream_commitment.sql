-- V8 FR-69: the dated commitment that launches a dream. All nullable: a dream
-- with no commitment simply has not been launched this way.
ALTER TABLE dreams ADD COLUMN commitment_signed_name VARCHAR(160);
ALTER TABLE dreams ADD COLUMN commitment_start_date DATE;
ALTER TABLE dreams ADD COLUMN commitment_first_action VARCHAR(1000);
ALTER TABLE dreams ADD COLUMN committed_at TIMESTAMP WITH TIME ZONE;
