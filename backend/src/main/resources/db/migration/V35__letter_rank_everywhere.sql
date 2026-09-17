-- Extends the Dream letter-rank pattern (single uppercase A-Z, ties
-- allowed, diagnostic only — never gates anything) to every other
-- rankable list: Vision Areas, Goals, Tasks, Obstacles, and Reviews.
-- Additive and nullable, matching V30__dream_letter_rank.sql exactly.
ALTER TABLE vision_areas ADD COLUMN letter_rank VARCHAR(1);
ALTER TABLE goals ADD COLUMN letter_rank VARCHAR(1);
ALTER TABLE task_items ADD COLUMN letter_rank VARCHAR(1);
ALTER TABLE obstacles ADD COLUMN letter_rank VARCHAR(1);
ALTER TABLE reviews ADD COLUMN letter_rank VARCHAR(1);
