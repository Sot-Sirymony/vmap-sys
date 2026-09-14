-- FR-57: an optional single-letter intra-area priority label (BR-46), a
-- label not a strict order — ties within a Vision Area are allowed.
ALTER TABLE dreams ADD COLUMN letter_rank VARCHAR(1);
