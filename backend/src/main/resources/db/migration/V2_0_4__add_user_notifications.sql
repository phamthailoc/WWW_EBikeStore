CREATE TABLE IF NOT EXISTS ebike_user.notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    target_url VARCHAR(255),
    order_id BIGINT REFERENCES ebike_order.orders(id) ON DELETE SET NULL,
    payment_id BIGINT REFERENCES ebike_order.payments(id) ON DELETE SET NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON ebike_user.notifications(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON ebike_user.notifications(user_id)
    WHERE read_at IS NULL;
