-- FR-31: extend the FR-14 moonshot concept from Goal to Dream — a dream can
-- be the ambitious swing, not only a goal beneath it. Aspirational metadata
-- only; it never affects progress or completion rules.
ALTER TABLE dreams ADD COLUMN moonshot BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dreams ADD COLUMN moonshot_vision VARCHAR(3000);
