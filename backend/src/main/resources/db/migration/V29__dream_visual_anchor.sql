-- FR-56: an optional link to an image representing the dream's fulfilled
-- state. Additive and nullable — no file storage involved.
ALTER TABLE dreams ADD COLUMN image_url VARCHAR(2048);
