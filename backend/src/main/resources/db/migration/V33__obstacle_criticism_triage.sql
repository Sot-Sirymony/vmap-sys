-- FR-60: incoming-criticism triage on PARTNER-type obstacles (BR-49).
-- All additive and nullable — diagnostic only, never gates status.
ALTER TABLE obstacles ADD COLUMN criticism_overstated VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN criticism_delivery VARCHAR(2000);
ALTER TABLE obstacles ADD COLUMN criticism_substance VARCHAR(2000);
