-- =====================================================
-- Migrate product image URLs from local paths to S3 URLs
-- Generated from backend/dataCawl/data/products_db_ready_clean.s3.json
-- =====================================================

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-sir-50cc-pearl-white.jpg',
    alt_text = 'KINETIC Sir 50CC - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-sir-50cc-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-sir-50cc' AND pv.sku = 'kinetic-sir-50cc-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-sir-50cc-matte-black.jpg',
    alt_text = 'KINETIC Sir 50CC - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-sir-50cc-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-sir-50cc' AND pv.sku = 'kinetic-sir-50cc-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-sir-50cc-ocean-blue.jpg',
    alt_text = 'KINETIC Sir 50CC - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-sir-50cc-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-sir-50cc' AND pv.sku = 'kinetic-sir-50cc-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-sir-50cc-cherry-red.jpg',
    alt_text = 'KINETIC Sir 50CC - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-sir-50cc-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-sir-50cc' AND pv.sku = 'kinetic-sir-50cc-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-pearl-white.jpg',
    alt_text = 'KINETIC Creer - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer' AND pv.sku = 'kinetic-creer-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-matte-black.jpg',
    alt_text = 'KINETIC Creer - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer' AND pv.sku = 'kinetic-creer-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-ocean-blue.jpg',
    alt_text = 'KINETIC Creer - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer' AND pv.sku = 'kinetic-creer-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-cherry-red.jpg',
    alt_text = 'KINETIC Creer - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer' AND pv.sku = 'kinetic-creer-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gofast-pearl-white.jpg',
    alt_text = 'KINETIC Gofast - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gofast-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gofast' AND pv.sku = 'kinetic-gofast-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gofast-matte-black.jpg',
    alt_text = 'KINETIC Gofast - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gofast-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gofast' AND pv.sku = 'kinetic-gofast-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gofast-ocean-blue.jpg',
    alt_text = 'KINETIC Gofast - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gofast-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gofast' AND pv.sku = 'kinetic-gofast-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gofast-cherry-red.jpg',
    alt_text = 'KINETIC Gofast - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gofast-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gofast' AND pv.sku = 'kinetic-gofast-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-ban-nang-cap-pearl-white.jpg',
    alt_text = 'KINETIC Pansy Xs1 Bản Nâng Cấp - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-ban-nang-cap-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND pv.sku = 'kinetic-pansy-xs1-ban-nang-cap-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-ban-nang-cap-matte-black.jpg',
    alt_text = 'KINETIC Pansy Xs1 Bản Nâng Cấp - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-ban-nang-cap-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND pv.sku = 'kinetic-pansy-xs1-ban-nang-cap-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-ban-nang-cap-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy Xs1 Bản Nâng Cấp - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-ban-nang-cap-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND pv.sku = 'kinetic-pansy-xs1-ban-nang-cap-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-ban-nang-cap-cherry-red.jpg',
    alt_text = 'KINETIC Pansy Xs1 Bản Nâng Cấp - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-ban-nang-cap-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1-ban-nang-cap' AND pv.sku = 'kinetic-pansy-xs1-ban-nang-cap-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs-pearl-white.jpg',
    alt_text = 'KINETIC Pansy Xs - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs' AND pv.sku = 'kinetic-pansy-xs-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs-matte-black.jpg',
    alt_text = 'KINETIC Pansy Xs - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs' AND pv.sku = 'kinetic-pansy-xs-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy Xs - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs' AND pv.sku = 'kinetic-pansy-xs-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs-cherry-red.jpg',
    alt_text = 'KINETIC Pansy Xs - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs' AND pv.sku = 'kinetic-pansy-xs-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-pearl-white.jpg',
    alt_text = 'KINETIC Pansy Xs1 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1' AND pv.sku = 'kinetic-pansy-xs1-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-matte-black.jpg',
    alt_text = 'KINETIC Pansy Xs1 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1' AND pv.sku = 'kinetic-pansy-xs1-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy Xs1 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1' AND pv.sku = 'kinetic-pansy-xs1-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-xs1-cherry-red.jpg',
    alt_text = 'KINETIC Pansy Xs1 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-xs1-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-xs1' AND pv.sku = 'kinetic-pansy-xs1-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-amy-pearl-white.jpg',
    alt_text = 'KINETIC Amy - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-amy-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-amy' AND pv.sku = 'kinetic-amy-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-amy-matte-black.jpg',
    alt_text = 'KINETIC Amy - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-amy-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-amy' AND pv.sku = 'kinetic-amy-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-amy-ocean-blue.jpg',
    alt_text = 'KINETIC Amy - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-amy-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-amy' AND pv.sku = 'kinetic-amy-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-amy-cherry-red.jpg',
    alt_text = 'KINETIC Amy - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-amy-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-amy' AND pv.sku = 'kinetic-amy-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ella-pearl-white.jpg',
    alt_text = 'KINETIC Ella - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ella-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ella' AND pv.sku = 'kinetic-ella-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ella-matte-black.jpg',
    alt_text = 'KINETIC Ella - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ella-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ella' AND pv.sku = 'kinetic-ella-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ella-ocean-blue.jpg',
    alt_text = 'KINETIC Ella - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ella-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ella' AND pv.sku = 'kinetic-ella-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ella-cherry-red.jpg',
    alt_text = 'KINETIC Ella - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ella-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ella' AND pv.sku = 'kinetic-ella-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-queen-pearl-white.jpg',
    alt_text = 'KINETIC Queen - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-queen-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-queen' AND pv.sku = 'kinetic-queen-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-queen-matte-black.jpg',
    alt_text = 'KINETIC Queen - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-queen-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-queen' AND pv.sku = 'kinetic-queen-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-queen-ocean-blue.jpg',
    alt_text = 'KINETIC Queen - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-queen-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-queen' AND pv.sku = 'kinetic-queen-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-queen-cherry-red.jpg',
    alt_text = 'KINETIC Queen - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-queen-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-queen' AND pv.sku = 'kinetic-queen-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ryan-pearl-white.jpg',
    alt_text = 'KINETIC Ryan - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ryan-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ryan' AND pv.sku = 'kinetic-ryan-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ryan-matte-black.jpg',
    alt_text = 'KINETIC Ryan - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ryan-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ryan' AND pv.sku = 'kinetic-ryan-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ryan-ocean-blue.jpg',
    alt_text = 'KINETIC Ryan - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ryan-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ryan' AND pv.sku = 'kinetic-ryan-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-ryan-cherry-red.jpg',
    alt_text = 'KINETIC Ryan - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-ryan-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ryan' AND pv.sku = 'kinetic-ryan-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-s-one-pearl-white.jpg',
    alt_text = 'KINETIC S One - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-s-one-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-s-one' AND pv.sku = 'kinetic-s-one-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-s-one-matte-black.jpg',
    alt_text = 'KINETIC S One - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-s-one-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-s-one' AND pv.sku = 'kinetic-s-one-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-s-one-ocean-blue.jpg',
    alt_text = 'KINETIC S One - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-s-one-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-s-one' AND pv.sku = 'kinetic-s-one-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/dd-kinetic-s-one-cherry-red.jpg',
    alt_text = 'KINETIC S One - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/dd-kinetic-s-one-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-s-one' AND pv.sku = 'kinetic-s-one-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-e-pearl-white.jpg',
    alt_text = 'KINETIC Creer E - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-e-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-e' AND pv.sku = 'kinetic-creer-e-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-e-matte-black.jpg',
    alt_text = 'KINETIC Creer E - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-e-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-e' AND pv.sku = 'kinetic-creer-e-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-e-ocean-blue.jpg',
    alt_text = 'KINETIC Creer E - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-e-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-e' AND pv.sku = 'kinetic-creer-e-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-e-cherry-red.jpg',
    alt_text = 'KINETIC Creer E - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-e-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-e' AND pv.sku = 'kinetic-creer-e-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-nile-pearl-white.jpg',
    alt_text = 'KINETIC Creer Nile - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-nile-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-nile' AND pv.sku = 'kinetic-creer-nile-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-nile-matte-black.jpg',
    alt_text = 'KINETIC Creer Nile - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-nile-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-nile' AND pv.sku = 'kinetic-creer-nile-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-nile-ocean-blue.jpg',
    alt_text = 'KINETIC Creer Nile - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-nile-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-nile' AND pv.sku = 'kinetic-creer-nile-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-creer-nile-cherry-red.jpg',
    alt_text = 'KINETIC Creer Nile - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-creer-nile-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-creer-nile' AND pv.sku = 'kinetic-creer-nile-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-pearl-white.jpg',
    alt_text = 'KINETIC Gogo Cross - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross' AND pv.sku = 'kinetic-gogo-cross-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-matte-black.jpg',
    alt_text = 'KINETIC Gogo Cross - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross' AND pv.sku = 'kinetic-gogo-cross-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-ocean-blue.jpg',
    alt_text = 'KINETIC Gogo Cross - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross' AND pv.sku = 'kinetic-gogo-cross-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-cherry-red.jpg',
    alt_text = 'KINETIC Gogo Cross - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross' AND pv.sku = 'kinetic-gogo-cross-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-g-pearl-white.jpg',
    alt_text = 'KINETIC Gogo Cross G - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-g-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross-g' AND pv.sku = 'kinetic-gogo-cross-g-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-g-matte-black.jpg',
    alt_text = 'KINETIC Gogo Cross G - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-g-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross-g' AND pv.sku = 'kinetic-gogo-cross-g-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-g-ocean-blue.jpg',
    alt_text = 'KINETIC Gogo Cross G - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-g-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross-g' AND pv.sku = 'kinetic-gogo-cross-g-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-cross-g-cherry-red.jpg',
    alt_text = 'KINETIC Gogo Cross G - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-cross-g-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-cross-g' AND pv.sku = 'kinetic-gogo-cross-g-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-crown-pearl-white.jpg',
    alt_text = 'KINETIC Gogo Crown - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-crown-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-crown' AND pv.sku = 'kinetic-gogo-crown-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-crown-matte-black.jpg',
    alt_text = 'KINETIC Gogo Crown - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-crown-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-crown' AND pv.sku = 'kinetic-gogo-crown-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-crown-ocean-blue.jpg',
    alt_text = 'KINETIC Gogo Crown - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-crown-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-crown' AND pv.sku = 'kinetic-gogo-crown-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-crown-cherry-red.jpg',
    alt_text = 'KINETIC Gogo Crown - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-crown-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-crown' AND pv.sku = 'kinetic-gogo-crown-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-moon-pearl-white.jpg',
    alt_text = 'KINETIC Gogo Moon - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-moon-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-moon' AND pv.sku = 'kinetic-gogo-moon-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-moon-matte-black.jpg',
    alt_text = 'KINETIC Gogo Moon - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-moon-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-moon' AND pv.sku = 'kinetic-gogo-moon-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-moon-ocean-blue.jpg',
    alt_text = 'KINETIC Gogo Moon - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-moon-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-moon' AND pv.sku = 'kinetic-gogo-moon-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-moon-cherry-red.jpg',
    alt_text = 'KINETIC Gogo Moon - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-moon-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-moon' AND pv.sku = 'kinetic-gogo-moon-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-keva-pearl-white.jpg',
    alt_text = 'KINETIC Keva - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-keva-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-keva' AND pv.sku = 'kinetic-keva-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-keva-matte-black.jpg',
    alt_text = 'KINETIC Keva - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-keva-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-keva' AND pv.sku = 'kinetic-keva-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-keva-ocean-blue.jpg',
    alt_text = 'KINETIC Keva - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-keva-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-keva' AND pv.sku = 'kinetic-keva-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-keva-cherry-red.jpg',
    alt_text = 'KINETIC Keva - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-keva-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-keva' AND pv.sku = 'kinetic-keva-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-ls007-pearl-white.jpg',
    alt_text = 'KINETIC Ls007 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-ls007-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ls007' AND pv.sku = 'kinetic-ls007-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-ls007-matte-black.jpg',
    alt_text = 'KINETIC Ls007 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-ls007-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ls007' AND pv.sku = 'kinetic-ls007-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-ls007-ocean-blue.jpg',
    alt_text = 'KINETIC Ls007 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-ls007-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ls007' AND pv.sku = 'kinetic-ls007-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-ls007-cherry-red.jpg',
    alt_text = 'KINETIC Ls007 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-ls007-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-ls007' AND pv.sku = 'kinetic-ls007-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-m-one-pearl-white.jpg',
    alt_text = 'KINETIC M One - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-m-one-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-m-one' AND pv.sku = 'kinetic-m-one-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-m-one-matte-black.jpg',
    alt_text = 'KINETIC M One - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-m-one-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-m-one' AND pv.sku = 'kinetic-m-one-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-m-one-ocean-blue.jpg',
    alt_text = 'KINETIC M One - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-m-one-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-m-one' AND pv.sku = 'kinetic-m-one-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-m-one-cherry-red.jpg',
    alt_text = 'KINETIC M One - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-m-one-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-m-one' AND pv.sku = 'kinetic-m-one-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-mxs-pearl-white.jpg',
    alt_text = 'KINETIC Mxs - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-mxs-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-mxs' AND pv.sku = 'kinetic-mxs-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-mxs-matte-black.jpg',
    alt_text = 'KINETIC Mxs - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-mxs-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-mxs' AND pv.sku = 'kinetic-mxs-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-mxs-ocean-blue.jpg',
    alt_text = 'KINETIC Mxs - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-mxs-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-mxs' AND pv.sku = 'kinetic-mxs-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-mxs-cherry-red.jpg',
    alt_text = 'KINETIC Mxs - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-mxs-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-mxs' AND pv.sku = 'kinetic-mxs-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-pearl-white.jpg',
    alt_text = 'KINETIC Pansy Dio - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio' AND pv.sku = 'kinetic-pansy-dio-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-matte-black.jpg',
    alt_text = 'KINETIC Pansy Dio - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio' AND pv.sku = 'kinetic-pansy-dio-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy Dio - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio' AND pv.sku = 'kinetic-pansy-dio-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-cherry-red.jpg',
    alt_text = 'KINETIC Pansy Dio - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio' AND pv.sku = 'kinetic-pansy-dio-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-2025-pearl-white.jpg',
    alt_text = 'KINETIC Pansy Dio 2025 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-2025-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio-2025' AND pv.sku = 'kinetic-pansy-dio-2025-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-2025-matte-black.jpg',
    alt_text = 'KINETIC Pansy Dio 2025 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-2025-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio-2025' AND pv.sku = 'kinetic-pansy-dio-2025-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-2025-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy Dio 2025 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-2025-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio-2025' AND pv.sku = 'kinetic-pansy-dio-2025-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-dio-2025-cherry-red.jpg',
    alt_text = 'KINETIC Pansy Dio 2025 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-dio-2025-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-dio-2025' AND pv.sku = 'kinetic-pansy-dio-2025-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s2-cang-nhom-pearl-white.jpg',
    alt_text = 'KINETIC Pansy S2 Càng Nhôm - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s2-cang-nhom-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s2-cang-nhom' AND pv.sku = 'kinetic-pansy-s2-cang-nhom-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s2-cang-nhom-matte-black.jpg',
    alt_text = 'KINETIC Pansy S2 Càng Nhôm - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s2-cang-nhom-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s2-cang-nhom' AND pv.sku = 'kinetic-pansy-s2-cang-nhom-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s2-cang-nhom-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy S2 Càng Nhôm - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s2-cang-nhom-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s2-cang-nhom' AND pv.sku = 'kinetic-pansy-s2-cang-nhom-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s2-cang-nhom-cherry-red.jpg',
    alt_text = 'KINETIC Pansy S2 Càng Nhôm - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s2-cang-nhom-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s2-cang-nhom' AND pv.sku = 'kinetic-pansy-s2-cang-nhom-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s3-pearl-white.jpg',
    alt_text = 'KINETIC Pansy S3 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s3-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s3' AND pv.sku = 'kinetic-pansy-s3-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s3-matte-black.jpg',
    alt_text = 'KINETIC Pansy S3 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s3-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s3' AND pv.sku = 'kinetic-pansy-s3-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s3-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy S3 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s3-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s3' AND pv.sku = 'kinetic-pansy-s3-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s3-cherry-red.jpg',
    alt_text = 'KINETIC Pansy S3 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s3-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s3' AND pv.sku = 'kinetic-pansy-s3-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s4-pearl-white.jpg',
    alt_text = 'KINETIC Pansy S4 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s4-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s4' AND pv.sku = 'kinetic-pansy-s4-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s4-matte-black.jpg',
    alt_text = 'KINETIC Pansy S4 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s4-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s4' AND pv.sku = 'kinetic-pansy-s4-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s4-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy S4 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s4-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s4' AND pv.sku = 'kinetic-pansy-s4-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-s4-cherry-red.jpg',
    alt_text = 'KINETIC Pansy S4 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-s4-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-s4' AND pv.sku = 'kinetic-pansy-s4-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-sq2-pearl-white.jpg',
    alt_text = 'KINETIC Pansy Sq2 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-sq2-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-sq2' AND pv.sku = 'kinetic-pansy-sq2-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-sq2-matte-black.jpg',
    alt_text = 'KINETIC Pansy Sq2 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-sq2-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-sq2' AND pv.sku = 'kinetic-pansy-sq2-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-sq2-ocean-blue.jpg',
    alt_text = 'KINETIC Pansy Sq2 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-sq2-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-sq2' AND pv.sku = 'kinetic-pansy-sq2-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-pansy-sq2-cherry-red.jpg',
    alt_text = 'KINETIC Pansy Sq2 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-pansy-sq2-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-pansy-sq2' AND pv.sku = 'kinetic-pansy-sq2-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rosa-pearl-white.jpg',
    alt_text = 'KINETIC Rosa - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rosa-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rosa' AND pv.sku = 'kinetic-rosa-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rosa-matte-black.jpg',
    alt_text = 'KINETIC Rosa - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rosa-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rosa' AND pv.sku = 'kinetic-rosa-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rosa-ocean-blue.jpg',
    alt_text = 'KINETIC Rosa - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rosa-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rosa' AND pv.sku = 'kinetic-rosa-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rosa-cherry-red.jpg',
    alt_text = 'KINETIC Rosa - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rosa-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rosa' AND pv.sku = 'kinetic-rosa-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rs-50cc-pearl-white.jpg',
    alt_text = 'KINETIC Rs 50CC - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rs-50cc-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rs-50cc' AND pv.sku = 'kinetic-rs-50cc-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rs-50cc-matte-black.jpg',
    alt_text = 'KINETIC Rs 50CC - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rs-50cc-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rs-50cc' AND pv.sku = 'kinetic-rs-50cc-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rs-50cc-ocean-blue.jpg',
    alt_text = 'KINETIC Rs 50CC - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rs-50cc-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rs-50cc' AND pv.sku = 'kinetic-rs-50cc-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-rs-50cc-cherry-red.jpg',
    alt_text = 'KINETIC Rs 50CC - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-rs-50cc-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-rs-50cc' AND pv.sku = 'kinetic-rs-50cc-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-pearl-white.jpg',
    alt_text = 'KINETIC Tesla Chic - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic' AND pv.sku = 'kinetic-tesla-chic-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-matte-black.jpg',
    alt_text = 'KINETIC Tesla Chic - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic' AND pv.sku = 'kinetic-tesla-chic-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-ocean-blue.jpg',
    alt_text = 'KINETIC Tesla Chic - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic' AND pv.sku = 'kinetic-tesla-chic-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-cherry-red.jpg',
    alt_text = 'KINETIC Tesla Chic - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic' AND pv.sku = 'kinetic-tesla-chic-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-q-pearl-white.jpg',
    alt_text = 'KINETIC Tesla Chic Q - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-q-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic-q' AND pv.sku = 'kinetic-tesla-chic-q-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-q-matte-black.jpg',
    alt_text = 'KINETIC Tesla Chic Q - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-q-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic-q' AND pv.sku = 'kinetic-tesla-chic-q-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-q-ocean-blue.jpg',
    alt_text = 'KINETIC Tesla Chic Q - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-q-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic-q' AND pv.sku = 'kinetic-tesla-chic-q-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-chic-q-cherry-red.jpg',
    alt_text = 'KINETIC Tesla Chic Q - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-chic-q-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-chic-q' AND pv.sku = 'kinetic-tesla-chic-q-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-pearl-white.jpg',
    alt_text = 'KINETIC Tesla Dio - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio' AND pv.sku = 'kinetic-tesla-dio-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-matte-black.jpg',
    alt_text = 'KINETIC Tesla Dio - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio' AND pv.sku = 'kinetic-tesla-dio-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-ocean-blue.jpg',
    alt_text = 'KINETIC Tesla Dio - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio' AND pv.sku = 'kinetic-tesla-dio-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-cherry-red.jpg',
    alt_text = 'KINETIC Tesla Dio - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio' AND pv.sku = 'kinetic-tesla-dio-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-e-pearl-white.jpg',
    alt_text = 'KINETIC Tesla Dio E - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-e-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio-e' AND pv.sku = 'kinetic-tesla-dio-e-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-e-matte-black.jpg',
    alt_text = 'KINETIC Tesla Dio E - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-e-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio-e' AND pv.sku = 'kinetic-tesla-dio-e-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-e-ocean-blue.jpg',
    alt_text = 'KINETIC Tesla Dio E - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-e-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio-e' AND pv.sku = 'kinetic-tesla-dio-e-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-dio-e-cherry-red.jpg',
    alt_text = 'KINETIC Tesla Dio E - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-dio-e-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-dio-e' AND pv.sku = 'kinetic-tesla-dio-e-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-e-pearl-white.jpg',
    alt_text = 'KINETIC Tesla E - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-e-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-e' AND pv.sku = 'kinetic-tesla-e-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-e-matte-black.jpg',
    alt_text = 'KINETIC Tesla E - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-e-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-e' AND pv.sku = 'kinetic-tesla-e-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-e-ocean-blue.jpg',
    alt_text = 'KINETIC Tesla E - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-e-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-e' AND pv.sku = 'kinetic-tesla-e-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-e-cherry-red.jpg',
    alt_text = 'KINETIC Tesla E - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-e-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-e' AND pv.sku = 'kinetic-tesla-e-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-sd-pearl-white.jpg',
    alt_text = 'KINETIC Tesla Sd - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-sd-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-sd' AND pv.sku = 'kinetic-tesla-sd-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-sd-matte-black.jpg',
    alt_text = 'KINETIC Tesla Sd - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-sd-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-sd' AND pv.sku = 'kinetic-tesla-sd-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-sd-ocean-blue.jpg',
    alt_text = 'KINETIC Tesla Sd - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-sd-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-sd' AND pv.sku = 'kinetic-tesla-sd-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-sd-cherry-red.jpg',
    alt_text = 'KINETIC Tesla Sd - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-sd-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-sd' AND pv.sku = 'kinetic-tesla-sd-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-xman-leo-pearl-white.jpg',
    alt_text = 'KINETIC Xman Leo - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-xman-leo-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-xman-leo' AND pv.sku = 'kinetic-xman-leo-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-xman-leo-matte-black.jpg',
    alt_text = 'KINETIC Xman Leo - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-xman-leo-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-xman-leo' AND pv.sku = 'kinetic-xman-leo-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-xman-leo-ocean-blue.jpg',
    alt_text = 'KINETIC Xman Leo - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-xman-leo-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-xman-leo' AND pv.sku = 'kinetic-xman-leo-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-xman-leo-cherry-red.jpg',
    alt_text = 'KINETIC Xman Leo - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-xman-leo-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-xman-leo' AND pv.sku = 'kinetic-xman-leo-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-x-pearl-white.jpg',
    alt_text = 'KINETIC Tesla X - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-x-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-x' AND pv.sku = 'kinetic-tesla-x-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-x-matte-black.jpg',
    alt_text = 'KINETIC Tesla X - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-x-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-x' AND pv.sku = 'kinetic-tesla-x-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-x-ocean-blue.jpg',
    alt_text = 'KINETIC Tesla X - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-x-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-x' AND pv.sku = 'kinetic-tesla-x-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-tesla-x-cherry-red.jpg',
    alt_text = 'KINETIC Tesla X - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-tesla-x-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-tesla-x' AND pv.sku = 'kinetic-tesla-x-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-butterfly-pearl-white.jpg',
    alt_text = 'KINETIC Butterfly - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-butterfly-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-butterfly' AND pv.sku = 'kinetic-butterfly-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-butterfly-matte-black.jpg',
    alt_text = 'KINETIC Butterfly - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-butterfly-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-butterfly' AND pv.sku = 'kinetic-butterfly-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-butterfly-ocean-blue.jpg',
    alt_text = 'KINETIC Butterfly - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-butterfly-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-butterfly' AND pv.sku = 'kinetic-butterfly-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-butterfly-cherry-red.jpg',
    alt_text = 'KINETIC Butterfly - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-butterfly-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-butterfly' AND pv.sku = 'kinetic-butterfly-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s4-pearl-white.jpg',
    alt_text = 'KINETIC Gogo S4 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s4-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s4' AND pv.sku = 'kinetic-gogo-s4-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s4-matte-black.jpg',
    alt_text = 'KINETIC Gogo S4 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s4-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s4' AND pv.sku = 'kinetic-gogo-s4-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s4-ocean-blue.jpg',
    alt_text = 'KINETIC Gogo S4 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s4-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s4' AND pv.sku = 'kinetic-gogo-s4-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s4-cherry-red.jpg',
    alt_text = 'KINETIC Gogo S4 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s4-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s4' AND pv.sku = 'kinetic-gogo-s4-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s5-pearl-white.jpg',
    alt_text = 'KINETIC Gogo S5 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s5-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s5' AND pv.sku = 'kinetic-gogo-s5-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s5-matte-black.jpg',
    alt_text = 'KINETIC Gogo S5 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s5-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s5' AND pv.sku = 'kinetic-gogo-s5-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s5-ocean-blue.jpg',
    alt_text = 'KINETIC Gogo S5 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s5-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s5' AND pv.sku = 'kinetic-gogo-s5-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-gogo-s5-cherry-red.jpg',
    alt_text = 'KINETIC Gogo S5 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-gogo-s5-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-gogo-s5' AND pv.sku = 'kinetic-gogo-s5-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-jeek-one-pearl-white.jpg',
    alt_text = 'KINETIC Jeek One - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-jeek-one-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-jeek-one' AND pv.sku = 'kinetic-jeek-one-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-jeek-one-matte-black.jpg',
    alt_text = 'KINETIC Jeek One - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-jeek-one-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-jeek-one' AND pv.sku = 'kinetic-jeek-one-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-jeek-one-ocean-blue.jpg',
    alt_text = 'KINETIC Jeek One - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-jeek-one-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-jeek-one' AND pv.sku = 'kinetic-jeek-one-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-jeek-one-cherry-red.jpg',
    alt_text = 'KINETIC Jeek One - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-jeek-one-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-jeek-one' AND pv.sku = 'kinetic-jeek-one-cherry-red';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-r1-pearl-white.jpg',
    alt_text = 'KINETIC R1 - Pearl White',
    sort_order = 0,
    is_primary = TRUE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-r1-pearl-white.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-r1' AND pv.sku = 'kinetic-r1-pearl-white';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-r1-matte-black.jpg',
    alt_text = 'KINETIC R1 - Matte Black',
    sort_order = 1,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-r1-matte-black.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-r1' AND pv.sku = 'kinetic-r1-matte-black';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-r1-ocean-blue.jpg',
    alt_text = 'KINETIC R1 - Ocean Blue',
    sort_order = 2,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-r1-ocean-blue.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-r1' AND pv.sku = 'kinetic-r1-ocean-blue';

UPDATE ebike_product.product_images pi
SET image_url = 'https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/md-kinetic-r1-cherry-red.jpg',
    alt_text = 'KINETIC R1 - Cherry Red',
    sort_order = 3,
    is_primary = FALSE,
    storage_provider = 'S3',
    storage_bucket = 'kinetic-s3-bucket',
    storage_region = 'ap-southeast-1',
    s3_key = 'products/md-kinetic-r1-cherry-red.jpg',
    status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM ebike_product.product_variants pv
JOIN ebike_product.products p ON p.id = pv.product_id
WHERE pi.variant_id = pv.id AND p.slug = 'kinetic-r1' AND pv.sku = 'kinetic-r1-cherry-red';
