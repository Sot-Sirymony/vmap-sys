-- V17 phase 3 (BRD 17.0.0, BL-FR-18 to BL-FR-21): a step can name the kind
-- of gap it has, and a "find help" step remembers what it was created to
-- cover and the partner record it created. Additive and nullable: no data is
-- rewritten (BL-NFR-01).
--
--   gap_kind            BL-FR-18: Knowledge, Skill, Time, Money, ... (the ObstacleType values)
--   covers_step_id      BL-FR-19, BL-FR-21: the step whose gap this step covers
--   covers_obstacle_id  BL-FR-20, BL-FR-21: the obstacle this step was made from
--   created_partner_id  BL-FR-21: the To Contact partner created with this step
--
-- Rollback: ALTER TABLE vision_steps DROP COLUMN created_partner_id,
-- DROP COLUMN covers_obstacle_id, DROP COLUMN covers_step_id, DROP COLUMN gap_kind;

ALTER TABLE vision_steps ADD COLUMN gap_kind VARCHAR(20);
ALTER TABLE vision_steps ADD COLUMN covers_step_id BIGINT REFERENCES vision_steps(id) ON DELETE SET NULL;
ALTER TABLE vision_steps ADD COLUMN covers_obstacle_id BIGINT REFERENCES obstacles(id) ON DELETE SET NULL;
ALTER TABLE vision_steps ADD COLUMN created_partner_id BIGINT REFERENCES partners(id) ON DELETE SET NULL;

CREATE INDEX idx_vision_steps_covers_step ON vision_steps(covers_step_id);
