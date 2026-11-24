-- V31 v2 (BRD 31 DC-FR-66): roles an admin adds to the owner check (R-07), one
-- per line or separated by commas, for example "Translator". Empty keeps the
-- built-in role words only.
ALTER TABLE ai_settings ADD COLUMN owner_roles VARCHAR(2000);
