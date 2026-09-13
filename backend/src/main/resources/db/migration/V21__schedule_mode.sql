-- FR-51: bottom-up target-date cascade. BOTTOM_UP is the default — a
-- parent's target date must not precede its latest active child's date
-- (BR-40). TOP_DOWN_FIXED opts a specific Dream/Goal out for a real
-- external hard deadline that cannot move. Additive, backfilled to the
-- default so existing rows behave exactly as they do today.
ALTER TABLE dreams ADD COLUMN schedule_mode VARCHAR(20) NOT NULL DEFAULT 'BOTTOM_UP';
ALTER TABLE goals ADD COLUMN schedule_mode VARCHAR(20) NOT NULL DEFAULT 'BOTTOM_UP';
