-- Explicit display order for the Vision Map tree's drag-and-drop reorder.
-- Additive and nullable; VisionStep already has sequence_number and reuses
-- it for the same purpose instead of gaining a second order column.
ALTER TABLE goals ADD COLUMN sort_order INTEGER;
ALTER TABLE task_items ADD COLUMN sort_order INTEGER;
