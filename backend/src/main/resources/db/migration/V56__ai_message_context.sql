-- V9 Phase 4 (AI-FR-05): which of the participant's records a Dream Coach
-- reply was based on, for the short reason shown under it ("based on your plan
-- and fear log"). A comma-separated list of source keys, never the data itself.
ALTER TABLE ai_messages ADD COLUMN context_sources VARCHAR(300);
