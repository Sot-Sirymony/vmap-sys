-- V29 (BRD 29, D-29-1): why a dream, goal, step or task was archived. Optional,
-- set when the item (or a parent, by the cascade) is archived, cleared on restore.
ALTER TABLE dreams ADD COLUMN archive_reason VARCHAR(1000);
ALTER TABLE goals ADD COLUMN archive_reason VARCHAR(1000);
ALTER TABLE vision_steps ADD COLUMN archive_reason VARCHAR(1000);
ALTER TABLE task_items ADD COLUMN archive_reason VARCHAR(1000);
