package com.ebike.dto.product.response;

import com.ebike.model.entity.product.ProductImageStatus;
import com.ebike.model.entity.product.StorageProvider;
import java.time.LocalDateTime;

public record AdminProductImageDto(
    Long id,
    Long productId,
    Long variantId,
    String imageUrl,
    String altText,
    Integer sortOrder,
    Boolean primaryImage,
    StorageProvider storageProvider,
    String storageBucket,
    String storageRegion,
    String s3Key,
    String mimeType,
    Long fileSize,
    String checksumSha256,
    ProductImageStatus status,
    LocalDateTime deletedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
