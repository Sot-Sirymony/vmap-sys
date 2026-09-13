-- FR-53: widens the FR-16 diligence checklist from five checks to ten
-- (planning and execution quality), plus a computed score. All additive
-- and nullable — existing reviews saved under the five-item rule are
-- unaffected until next saved, matching BR-42's widened all-or-nothing rule.
ALTER TABLE reviews ADD COLUMN diligence_rightly_planned BOOLEAN;
ALTER TABLE reviews ADD COLUMN diligence_rightly_performed BOOLEAN;
ALTER TABLE reviews ADD COLUMN diligence_expeditious BOOLEAN;
ALTER TABLE reviews ADD COLUMN diligence_efficient BOOLEAN;
ALTER TABLE reviews ADD COLUMN diligence_quality_outcome BOOLEAN;
ALTER TABLE reviews ADD COLUMN diligence_score_percent INTEGER;
