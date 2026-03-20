-- Content generation: bump on My Photos / accept / reject; processed_generation advances after each OpenAI run

ALTER TABLE album_suggestion_user_state
ADD COLUMN IF NOT EXISTS content_generation INTEGER NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS processed_generation INTEGER NOT NULL DEFAULT -1;
