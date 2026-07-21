-- FR-32: "creative persistence" for obstacles — a root cause captured before
-- an obstacle is marked Resolved, and at least three brainstormed
-- alternatives (one per line) before it's marked Accepted (giving up).
-- Both are diagnostic metadata only; enforcement lives in ObstacleService.
ALTER TABLE obstacles ADD COLUMN root_cause VARCHAR(3000);
ALTER TABLE obstacles ADD COLUMN creative_alternatives VARCHAR(3000);
