-- FR-33: a written vision statement per Vision Area — "what does this area
-- look like when it's going well?" — one level above a Dream's own
-- whyImportant/successDefinition. Optional; coaching, never a save gate.
ALTER TABLE vision_areas ADD COLUMN vision_statement VARCHAR(3000);
