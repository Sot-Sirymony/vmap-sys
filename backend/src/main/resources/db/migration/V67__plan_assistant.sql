-- V11 Phase 7 (PB-FR-50): a plan assistant conversation is a Dream Coach
-- conversation tagged with the dream and the plan section it was started on,
-- so the assistant can pick it up again for that section. Both are empty for
-- an ordinary Dream Coach chat. Removing a dream keeps the conversation.
ALTER TABLE ai_conversations ADD COLUMN dream_id BIGINT REFERENCES dreams(id) ON DELETE SET NULL;
ALTER TABLE ai_conversations ADD COLUMN section_key VARCHAR(40);

CREATE INDEX idx_ai_conversations_section ON ai_conversations (user_id, dream_id, section_key);
