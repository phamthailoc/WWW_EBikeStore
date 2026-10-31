CREATE TABLE IF NOT EXISTS ebike_order.order_email_verification_sessions (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(64) NOT NULL,
    otp_expires_at TIMESTAMP NOT NULL,
    resend_available_at TIMESTAMP NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMP,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_email_verification_email_created
    ON ebike_order.order_email_verification_sessions(email, created_at DESC);

ALTER TABLE ebike_order.orders
    DROP COLUMN IF EXISTS customer_identity_number;
