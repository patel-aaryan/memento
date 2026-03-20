-- Tracks last OpenAI call time per user (per-user cooldown between vision requests)

CREATE TABLE IF NOT EXISTS album_suggestion_user_state (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_openai_at TIMESTAMP WITH TIME ZONE
);
