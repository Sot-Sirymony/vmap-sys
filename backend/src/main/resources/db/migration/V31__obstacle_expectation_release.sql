-- FR-61: naming the specific expectation behind a PARTNER-type conflict,
-- plus a one-time "released" stamp (BR-50). All additive and nullable.
ALTER TABLE obstacles ADD COLUMN conflict_expectation VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN conflict_expectation_agreed VARCHAR(10);
ALTER TABLE obstacles ADD COLUMN expectation_released_at TIMESTAMP;
