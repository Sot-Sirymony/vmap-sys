-- V26 (BRD 26.0.0) phase 5, D-26-7: Modern is the only default interface style.
--
-- Q-26-1 was answered "keep CLASSIC" (product owner, 23 Nov 2026): no stored
-- style is changed here. Only the default for new accounts moves to MODERN,
-- and each account now records where its style came from, so a deliberate
-- choice is tracked on the account instead of per browser.
--
--   DEFAULT  never picked in Appearance; holds whatever the default was then
--   CHOSEN   picked by the user (set by PUT /api/preferences/appearance)
ALTER TABLE app_users ALTER COLUMN interface_style SET DEFAULT 'MODERN';

ALTER TABLE app_users ADD COLUMN interface_style_source VARCHAR(10) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_interface_style_source
    CHECK (interface_style_source IN ('DEFAULT', 'CHOSEN'));

-- MODERN and LIQUID_GLASS were never defaults before this migration, so an
-- account holding one of them got it by a choice (its own, or the app's old
-- per-browser switch saving MODERN back). CLASSIC accounts stay DEFAULT: they
-- may or may not have chosen it, and under Q-26-1 they keep it either way.
UPDATE app_users SET interface_style_source = 'CHOSEN'
WHERE interface_style IN ('MODERN', 'LIQUID_GLASS');
