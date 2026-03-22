-- Add location_name to images so we can persist a human-readable
-- place name for each photo and avoid re-calling external APIs.

ALTER TABLE images
ADD COLUMN IF NOT EXISTS location_name TEXT;

-- Optional index if we ever want to filter/group by location_name.
-- CREATE INDEX IF NOT EXISTS idx_images_location_name ON images(location_name);

