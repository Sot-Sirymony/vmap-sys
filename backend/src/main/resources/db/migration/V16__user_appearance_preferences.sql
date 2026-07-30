-- FR-39: appearance & theme preferences stored on the user's account
-- (FR-39.6), so a chosen look follows the user across browsers and devices
-- instead of living in one browser's localStorage.
--
-- Additive, and NOT NULL with defaults equal to the frontend's existing
-- defaults (System mode, Blue, Comfortable, Medium, both accessibility
-- toggles off). Every existing row therefore gets a valid value with no
-- backfill step, and the read path never has to handle NULL (BR-33).
--
-- theme_preset records the preset the user last applied. It is derivable from
-- (theme_mode, theme_accent) — a preset is a bundle of those two knobs, not a
-- hidden extra dimension (FR-39.1) — and is stored only to preserve the label
-- the user picked. 'CUSTOM' is what changing an individual control leaves
-- behind.
ALTER TABLE app_users ADD COLUMN theme_preset VARCHAR(40) NOT NULL DEFAULT 'FLUENT_SYSTEM';
ALTER TABLE app_users ADD COLUMN theme_mode VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE app_users ADD COLUMN theme_accent VARCHAR(20) NOT NULL DEFAULT 'BLUE';
ALTER TABLE app_users ADD COLUMN ui_density VARCHAR(20) NOT NULL DEFAULT 'COMFORTABLE';
ALTER TABLE app_users ADD COLUMN font_size VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE app_users ADD COLUMN high_contrast BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN reduce_motion BOOLEAN NOT NULL DEFAULT FALSE;
