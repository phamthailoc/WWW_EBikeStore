-- =====================================================
-- User favorite products
-- =====================================================

CREATE SCHEMA IF NOT EXISTS ebike_user;
CREATE SCHEMA IF NOT EXISTS ebike_auth;
CREATE SCHEMA IF NOT EXISTS ebike_product;

CREATE TABLE IF NOT EXISTS ebike_user.user_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES ebike_auth.users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES ebike_product.products(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_favorites_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_user_favorites_user_id
ON ebike_user.user_favorites(user_id);

CREATE INDEX IF NOT EXISTS idx_user_favorites_product_id
ON ebike_user.user_favorites(product_id);
