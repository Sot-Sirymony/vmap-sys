-- FR-49.3: the user's own estimate of a partner's work style — purely
-- descriptive metadata, never a partner self-report (no partner login).
ALTER TABLE partners ADD COLUMN work_style_type VARCHAR(20);
