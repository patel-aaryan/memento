-- Add optional audio URL to images (Cloudinary URL after upload)
ALTER TABLE images ADD COLUMN IF NOT EXISTS audio_url VARCHAR(500);
