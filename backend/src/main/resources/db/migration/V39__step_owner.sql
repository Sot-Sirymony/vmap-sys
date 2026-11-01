-- V8 FR-64: every step names who is responsible for it. Existing steps belong
-- to the only person who could have written them, so the owner backfills from
-- the account's name. target_date stays nullable here: old undated steps keep
-- working, and the date becomes required the next time one is saved in full.
ALTER TABLE vision_steps ADD COLUMN owner VARCHAR(160);
UPDATE vision_steps SET owner = (SELECT full_name FROM app_users WHERE app_users.id = vision_steps.user_id);
ALTER TABLE vision_steps ALTER COLUMN owner SET NOT NULL;
