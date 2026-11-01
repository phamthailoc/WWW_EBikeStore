-- =====================================================
-- Add S3-ready metadata to product images
-- =====================================================

ALTER TABLE ebike_product.product_images
    ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(255),
    ADD COLUMN IF NOT EXISTS storage_region VARCHAR(100),
    ADD COLUMN IF NOT EXISTS s3_key TEXT,
    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS checksum_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE ebike_product.product_images
SET storage_provider = COALESCE(NULLIF(storage_provider, ''), 'LOCAL'),
    status = COALESCE(NULLIF(status, ''), 'ACTIVE'),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP);

ALTER TABLE ebike_product.product_images
    ADD CONSTRAINT chk_product_images_storage_provider
        CHECK (storage_provider IN ('LOCAL', 'S3'));

ALTER TABLE ebike_product.product_images
    ADD CONSTRAINT chk_product_images_status
        CHECK (status IN ('ACTIVE', 'PENDING_DELETE', 'DELETED'));

CREATE INDEX IF NOT EXISTS idx_product_images_variant_id
    ON ebike_product.product_images(variant_id);

CREATE INDEX IF NOT EXISTS idx_product_images_status
    ON ebike_product.product_images(status);

CREATE INDEX IF NOT EXISTS idx_product_images_storage_provider
    ON ebike_product.product_images(storage_provider);

CREATE INDEX IF NOT EXISTS idx_product_images_s3_key
    ON ebike_product.product_images(s3_key)
    WHERE s3_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_product_images_active_product
    ON ebike_product.product_images(product_id, sort_order)
    WHERE status = 'ACTIVE';
