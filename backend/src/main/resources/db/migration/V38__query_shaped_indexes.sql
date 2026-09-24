-- Indexes shaped like the queries the app actually runs.
--
-- Nearly every read in this system is "this user's unarchived rows", often
-- narrowed to one parent, and sometimes ordered by time. The existing indexes
-- from V2/V15/V34 cover only the leading column, so the database finds every
-- row the user owns and then discards the archived ones, and sorts by hand
-- whenever a query is time-ordered. The composites below let it skip the
-- archived rows outright and walk time-ordered reads in index order.
--
-- Purely additive: the older single-column indexes are now redundant on
-- PostgreSQL (each is the leading column of one created here), but they are
-- deliberately NOT dropped. H2, which the test profile runs, backs a foreign
-- key constraint with whatever index already covers its column and refuses to
-- drop it ("index ... belongs to constraint"). Dropping them would mean the
-- test database no longer runs the same migrations as production, which costs
-- more than a few duplicate b-trees do. If this ever moves to a
-- PostgreSQL-backed test database, they can go in a follow-up migration.

-- "This user's unarchived rows": the shape behind every list endpoint,
-- the dashboard's loaders, and the Excel export.
CREATE INDEX idx_vision_areas_user_archived ON vision_areas (user_id, archived);
CREATE INDEX idx_dreams_user_archived ON dreams (user_id, archived);
CREATE INDEX idx_goals_user_archived ON goals (user_id, archived);
CREATE INDEX idx_vision_steps_user_archived ON vision_steps (user_id, archived);
CREATE INDEX idx_task_items_user_archived ON task_items (user_id, archived);
CREATE INDEX idx_partners_user_archived ON partners (user_id, archived);
CREATE INDEX idx_obstacles_user_archived ON obstacles (user_id, archived);
CREATE INDEX idx_communication_messages_user_archived ON communication_messages (user_id, archived);
CREATE INDEX idx_issue_reports_user_archived ON issue_reports (user_id, archived);

-- "This parent's unarchived children": the Vision Map tree, the progress
-- roll-up, the archive cascade, and the sort-order count on every create.
CREATE INDEX idx_dreams_area_user_archived ON dreams (vision_area_id, user_id, archived);
CREATE INDEX idx_goals_dream_user_archived ON goals (dream_id, user_id, archived);
CREATE INDEX idx_vision_steps_goal_user_archived ON vision_steps (goal_id, user_id, archived);
CREATE INDEX idx_task_items_step_user_archived ON task_items (step_id, user_id, archived);

-- Time-ordered reads. The trailing column is what the query sorts or ranges
-- on, so the database can walk the index instead of sorting the rows.
CREATE INDEX idx_reviews_user_archived_date ON reviews (user_id, archived, review_date);
CREATE INDEX idx_gratitude_entries_user_archived_created ON gratitude_entries (user_id, archived, created_at);
CREATE INDEX idx_progress_logs_user_archived_logged ON progress_logs (user_id, archived, logged_at);
-- The dashboard trend's per-task subquery ("this task's last entry before the
-- window"), which is a max() over one task's history.
CREATE INDEX idx_progress_logs_task_archived_logged ON progress_logs (related_task_id, archived, logged_at);

-- Partner links, read as "does this dream/goal already have a partner" on the
-- decision gates rather than as a list.
CREATE INDEX idx_partners_user_dream_archived ON partners (user_id, related_dream_id, archived);
CREATE INDEX idx_partners_user_goal_archived ON partners (user_id, related_goal_id, archived);
