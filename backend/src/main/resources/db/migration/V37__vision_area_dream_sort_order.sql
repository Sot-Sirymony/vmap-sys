-- Extends the Vision Map drag-and-drop reorder (V36, Goal/TaskItem) up to
-- Vision Area and Dream, so every list page can offer the same reorder.
ALTER TABLE vision_areas ADD COLUMN sort_order INTEGER;
ALTER TABLE dreams ADD COLUMN sort_order INTEGER;
