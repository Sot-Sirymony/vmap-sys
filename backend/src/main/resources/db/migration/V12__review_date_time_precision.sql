-- Review date/time: reviews previously only recorded a calendar date. Users
-- want to log the hour/minute/second a review happened, so review_date
-- becomes a full timestamp. Existing rows get midnight as their time-of-day.
ALTER TABLE reviews ALTER COLUMN review_date TYPE TIMESTAMP;
