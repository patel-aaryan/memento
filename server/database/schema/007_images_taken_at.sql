-- Add taken_at: when the photo was taken (from EXIF/photo metadata), nullable
ALTER TABLE images ADD COLUMN IF NOT EXISTS taken_at TIMESTAMP WITH TIME ZONE;
