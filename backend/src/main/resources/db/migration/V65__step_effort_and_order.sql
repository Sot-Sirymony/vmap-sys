-- V11 Phase 4 (PB-FR-12): a step's estimated effort, and the step it starts
-- after when order matters. Both optional. "Starts after" points at a step of
-- the same goal; if that step is deleted the link is simply cleared.
ALTER TABLE vision_steps ADD COLUMN estimated_hours NUMERIC(6, 2);
ALTER TABLE vision_steps ADD COLUMN starts_after_step_id BIGINT REFERENCES vision_steps(id) ON DELETE SET NULL;
