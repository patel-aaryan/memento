-- AI album suggestions (My Photos clustering) and refresh queue

CREATE TABLE IF NOT EXISTS album_suggestions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    album_name TEXT NOT NULL DEFAULT '',
    image_ids INTEGER[] NOT NULL DEFAULT '{}',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_album_suggestions_user_status
    ON album_suggestions (user_id, status);

CREATE INDEX IF NOT EXISTS idx_album_suggestions_created
    ON album_suggestions (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS album_suggestion_refresh_queue (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    run_after TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_album_suggestion_queue_run_after
    ON album_suggestion_refresh_queue (run_after);

-- Daily OpenAI call budget per user (UTC date)
CREATE TABLE IF NOT EXISTS album_suggestion_usage (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    api_calls INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, usage_date)
);

CREATE TRIGGER update_album_suggestions_updated_at BEFORE UPDATE ON album_suggestions
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
