-- Friends table (bidirectional: if A adds B, both are friends)
-- Store one row per friendship with user_a_id < user_b_id to avoid duplicates
CREATE TABLE IF NOT EXISTS friends (
    id SERIAL PRIMARY KEY,
    user_a_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_a_id, user_b_id),
    CHECK (user_a_id < user_b_id)
);

-- Indexes for friend lookups
CREATE INDEX IF NOT EXISTS idx_friends_user_a ON friends(user_a_id);
CREATE INDEX IF NOT EXISTS idx_friends_user_b ON friends(user_b_id);
