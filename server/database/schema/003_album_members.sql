-- Album members table (junction table for many-to-many relationship)
CREATE TABLE IF NOT EXISTS album_members (
    id SERIAL PRIMARY KEY,
    album_id INTEGER NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(album_id, user_id)  -- Prevent duplicate memberships
);

-- Create indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_album_members_album_id ON album_members(album_id);
CREATE INDEX IF NOT EXISTS idx_album_members_user_id ON album_members(user_id);

