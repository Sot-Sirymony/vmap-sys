-- V17 phase 5 (BRD 17.0.0, BL-FR-30, BL-FR-31, D-17-3): the six chains follow
-- the method's list. Two new chains (misplaced priorities, lack of
-- persistence) are added. Knowledge and resources move to the step gap check
-- (BL-FR-32), so they are no longer asked: their columns become nullable and
-- past assessments keep their values. No data is rewritten (BL-NFR-01).
--
-- Rollback (before redeploying the old build, which needs the retired scores):
--   UPDATE chain_assessments SET knowledge_score = 3 WHERE knowledge_score IS NULL;
--   UPDATE chain_assessments SET resources_score = 3 WHERE resources_score IS NULL;
--   ALTER TABLE chain_assessments ALTER COLUMN knowledge_score SET NOT NULL;
--   ALTER TABLE chain_assessments ALTER COLUMN resources_score SET NOT NULL;
--   ALTER TABLE chain_assessments DROP COLUMN priorities_score, DROP COLUMN persistence_score;
-- (3 is the documented neutral value; flag it in the rollback ticket.)

ALTER TABLE chain_assessments ADD COLUMN priorities_score INTEGER;
ALTER TABLE chain_assessments ADD COLUMN persistence_score INTEGER;
ALTER TABLE chain_assessments ALTER COLUMN knowledge_score DROP NOT NULL;
ALTER TABLE chain_assessments ALTER COLUMN resources_score DROP NOT NULL;
