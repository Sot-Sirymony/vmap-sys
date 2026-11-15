-- V17 phase 1 (BRD 17.0.0, D-17-1, BL-FR-02): an obstacle on a task or step
-- takes its step, goal and dream from that anchor. Before V17 these were
-- chosen by hand and could disagree (a goal from one dream, a step from
-- another). This data fix makes them agree.
--
-- Rules:
--   * Only anchored obstacles change (a task or step is set). Unplaced
--     obstacles (dream-only, goal-only, none) are left exactly as they are
--     (BL-FR-06). No anchor (task or step) is ever changed, except that a
--     task's own step wins over a stored step that disagrees.
--   * No row is deleted or archived (BL-NFR-01).
--   * Every changed row's old ids are kept in obstacle_anchor_fix_v75 for 90
--     days, so the fix can be reversed:
--
--       UPDATE obstacles SET
--           related_step_id  = (SELECT f.old_related_step_id  FROM obstacle_anchor_fix_v75 f WHERE f.obstacle_id = obstacles.id),
--           related_goal_id  = (SELECT f.old_related_goal_id  FROM obstacle_anchor_fix_v75 f WHERE f.obstacle_id = obstacles.id),
--           related_dream_id = (SELECT f.old_related_dream_id FROM obstacle_anchor_fix_v75 f WHERE f.obstacle_id = obstacles.id)
--       WHERE id IN (SELECT obstacle_id FROM obstacle_anchor_fix_v75);
--
-- Correlated subqueries (not UPDATE ... FROM) so the same script runs on
-- PostgreSQL and on the H2 test database.

CREATE TABLE obstacle_anchor_fix_v75 (
    obstacle_id BIGINT PRIMARY KEY,
    old_related_step_id BIGINT,
    old_related_goal_id BIGINT,
    old_related_dream_id BIGINT,
    fixed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO obstacle_anchor_fix_v75 (obstacle_id, old_related_step_id, old_related_goal_id, old_related_dream_id)
SELECT o.id, o.related_step_id, o.related_goal_id, o.related_dream_id
FROM obstacles o
LEFT JOIN task_items t ON t.id = o.related_task_id
JOIN vision_steps s ON s.id = COALESCE(t.step_id, o.related_step_id)
JOIN goals g ON g.id = s.goal_id
WHERE o.related_step_id IS DISTINCT FROM s.id
   OR o.related_goal_id IS DISTINCT FROM s.goal_id
   OR o.related_dream_id IS DISTINCT FROM g.dream_id;

UPDATE obstacles
SET related_step_id = (SELECT t.step_id FROM task_items t WHERE t.id = obstacles.related_task_id)
WHERE related_task_id IS NOT NULL
  AND id IN (SELECT obstacle_id FROM obstacle_anchor_fix_v75);

UPDATE obstacles
SET related_goal_id = (SELECT s.goal_id FROM vision_steps s WHERE s.id = obstacles.related_step_id)
WHERE related_step_id IS NOT NULL
  AND id IN (SELECT obstacle_id FROM obstacle_anchor_fix_v75);

UPDATE obstacles
SET related_dream_id = (SELECT g.dream_id FROM goals g WHERE g.id = obstacles.related_goal_id)
WHERE related_step_id IS NOT NULL
  AND id IN (SELECT obstacle_id FROM obstacle_anchor_fix_v75);

-- BL-FR-08 looks obstacles up by their step and task when those move.
CREATE INDEX idx_obstacles_related_step ON obstacles (related_step_id);
CREATE INDEX idx_obstacles_related_task ON obstacles (related_task_id);
