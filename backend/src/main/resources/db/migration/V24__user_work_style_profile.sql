-- FR-49: a one-time, retakeable work-style self-assessment. All six columns
-- are additive and nullable together — a user who never takes it has no
-- profile at all (BR-38), not a half-filled one.
ALTER TABLE app_users ADD COLUMN work_style_dominant VARCHAR(20);
ALTER TABLE app_users ADD COLUMN work_style_secondary VARCHAR(20);
ALTER TABLE app_users ADD COLUMN work_style_pace_fast_score INTEGER;
ALTER TABLE app_users ADD COLUMN work_style_pace_deliberate_score INTEGER;
ALTER TABLE app_users ADD COLUMN work_style_focus_task_score INTEGER;
ALTER TABLE app_users ADD COLUMN work_style_focus_people_score INTEGER;
