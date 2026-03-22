-- Pending friend requests (email-based). Link-based add remains instant mutual add.
CREATE TABLE IF NOT EXISTS friend_requests (
    id SERIAL PRIMARY KEY,
    requester_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(requester_id, recipient_id),
    CHECK (requester_id <> recipient_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_requests_recipient ON friend_requests(recipient_id);
CREATE INDEX IF NOT EXISTS idx_friend_requests_requester ON friend_requests(requester_id);
