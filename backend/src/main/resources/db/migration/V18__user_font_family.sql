-- FR-42: the typeface the app renders in.
--
-- Additive and NOT NULL with a default of 'SYSTEM', which is the existing
-- Segoe UI Variable / SF Pro / Roboto stack — a system font that loads nothing.
-- Existing users therefore keep exactly the typography they have, and anyone
-- who never opens the control never downloads a font file (FR-42.2).
ALTER TABLE app_users ADD COLUMN font_family VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';
