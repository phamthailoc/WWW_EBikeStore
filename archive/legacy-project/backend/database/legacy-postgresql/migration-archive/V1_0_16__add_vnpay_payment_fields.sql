ALTER TABLE ebike_order.payments
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'VND',
    ADD COLUMN IF NOT EXISTS provider_txn_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_response TEXT,
    ADD COLUMN IF NOT EXISTS vnpay_response_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS vnpay_bank_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS vnpay_card_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS vnpay_secure_hash VARCHAR(256);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_transaction_reference
    ON ebike_order.payments(transaction_reference)
    WHERE transaction_reference IS NOT NULL;
