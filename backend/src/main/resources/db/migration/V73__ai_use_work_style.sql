-- V15 (AH-FR-76): the participant's "Let AI helpers use my work style" switch.
-- Off by default until the sponsor decides otherwise. While it is off, no AI
-- request outside the Work Style page itself contains the participant's style;
-- while it is on, only the dominant and secondary style are sent.
ALTER TABLE app_users ADD COLUMN ai_use_work_style BOOLEAN NOT NULL DEFAULT FALSE;
