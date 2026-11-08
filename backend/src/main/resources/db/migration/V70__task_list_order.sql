-- The participant's own overall task order for the Tasks page ("do this
-- first, this second"), across every step. Separate from sort_order, which
-- is the order of tasks inside one step on the Vision Map. NULL until the
-- first drag on the Tasks page; NULLs sort last, by id.
ALTER TABLE task_items ADD COLUMN list_order INTEGER;
