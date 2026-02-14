-- Audio table
CREATE TABLE IF NOT EXISTS audio (
    id SERIAL PRIMARY KEY,
    image_id INTEGER NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_audio_image_id ON audio(image_id);

-- Create trigger to automatically update the updated_at timestamp
CREATE TRIGGER update_audio_updated_at BEFORE UPDATE ON audio
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

