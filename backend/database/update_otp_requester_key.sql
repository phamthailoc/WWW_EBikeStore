-- QA B1b: per-requester resend cooldown for order email OTP sessions. Safe to run more than once.
-- otp_hash keeps its width: the HMAC-SHA256 hex that replaces plain SHA-256 is also 64 chars (codes pending at deploy time stop working).
ALTER TABLE order_email_verification_sessions ADD COLUMN IF NOT EXISTS requester_key VARCHAR(64) NULL AFTER email;
CREATE INDEX IF NOT EXISTS idx_order_email_verification_email_requester ON order_email_verification_sessions (email, requester_key, created_at DESC);
