-- FR-34.1: tag a task by its energy demand (CHARGE / NEUTRAL / DRAIN) so the
-- weekly Energy Budget can net energising work against draining work. Additive
-- and nullable — existing tasks keep NULL, which the app reads as NEUTRAL
-- (BR-27); no backfill needed. Diagnostic metadata only: it never affects
-- progress, status, or completion rules.
ALTER TABLE task_items ADD COLUMN energy_demand VARCHAR(16);
