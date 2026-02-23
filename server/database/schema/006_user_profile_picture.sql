-- Add profile_picture_url to users (nullable URL for avatar)
ALTER TABLE users
ADD COLUMN IF NOT EXISTS profile_picture_url VARCHAR(2048) DEFAULT NULL;
