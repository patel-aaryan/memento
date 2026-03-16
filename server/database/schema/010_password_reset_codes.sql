-- One-time password reset codes
CREATE TABLE IF NOT EXISTS password_reset_codes (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_codes_user_id
    ON password_reset_codes(user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_codes_expires_at
    ON password_reset_codes(expires_at);
