-- V15 (AH-FR-57): admins turn the page assistant and helpers off per page, for
-- everyone (ai_settings) or for one cohort (cohorts). Each holds a comma-separated
-- list of page keys that are off: MODULES, MODULE, ASSESSMENT, OBSTACLES,
-- COMMUNICATION, WORK_STYLE. Empty means every page is on.
ALTER TABLE ai_settings ADD COLUMN pages_off VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE cohorts ADD COLUMN ai_pages_off VARCHAR(200) NOT NULL DEFAULT '';
