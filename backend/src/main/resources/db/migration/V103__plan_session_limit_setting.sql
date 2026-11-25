-- PB-NFR-03 follow-up: the AI requests one Build my plan session may use is now
-- an admin setting instead of a fixed 15. The default keeps today's limit.
ALTER TABLE ai_settings ADD COLUMN plan_session_limit INTEGER NOT NULL DEFAULT 15;
