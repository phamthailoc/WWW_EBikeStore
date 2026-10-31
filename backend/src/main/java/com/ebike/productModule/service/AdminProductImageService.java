package com.ebike.productModule.service;

import com.ebike.config.RedisCacheConfiguration;
import com.ebike.productModule.dto.response.AdminProductImageDto;
import com.ebike.productModule.entity.Product;
import com.ebike.productModule.entity.ProductImage;
import com.ebike.productModule.entity.ProductImageStatus;
import com.ebike.productModule.entity.ProductVariant;
import com.ebike.productModule.entity.StorageProvider;
import com.ebike.productModule.repository.ProductImageRepository;
import com.ebike.productModule.repository.ProductRepository;
import com.ebike.productModule.repository.ProductVariantRepository;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AdminProductImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminProductImageService.class);

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageStorageService storageService;

    public AdminProductImageService(
        ProductRepository productRepository,
        ProductVariantRepository productVariantRepository,
        ProductImageRepository productImageRepository,
        ProductImageStorageService storageService
    ) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.storageService = storageService;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public AdminProductImageDto uploadImage(
        Long productId,
        Long variantId,
        MultipartFile file,
        String altText,
        Integer sortOrder,
        Boolean primaryImage
    ) {
        Product product = findProduct(productId);
        ProductVariant variant = validateVariant(product, variantId);
        byte[] content = readImageBytes(file);
        String contentType = resolveContentType(file);
        String s3Key = buildS3Key(product.getId(), variant == null ? null : variant.getId(), file.getOriginalFilename());
        String publicUrl = storageService.upload(s3Key, content, contentType);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setVariant(variant);
        image.setImageUrl(publicUrl);
        image.setAltText(normalizeText(altText));
        image.setSortOrder(sortOrder == null ? 0 : sortOrder);
        image.setPrimaryImage(Boolean.TRUE.equals(primaryImage));
        image.setStorageProvider(StorageProvider.S3);
        image.setStorageBucket(storageService.getBucket());
        image.setStorageRegion(storageService.getRegion());
        image.setS3Key(s3Key);
        image.setMimeType(contentType);
        image.setFileSize(file.getSize());
        image.setChecksumSha256(sha256Hex(content));
        image.setStatus(ProductImageStatus.ACTIVE);

        if (Boolean.TRUE.equals(primaryImage)) {
            clearPrimaryImage(product, variant, null);
        }

        try {
            ProductImage saved = productImageRepository.save(image);
            return toDto(saved);
        } catch (RuntimeException exception) {
            tryDeleteStorageObject(s3Key);
            throw exception;
        }
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public AdminProductImageDto updateImage(
        Long imageId,
        MultipartFile file,
        String altText,
        Integer sortOrder,
        Boolean primaryImage
    ) {
        ProductImage image = findImage(imageId);
        String previousKey = image.getS3Key();
        StorageProvider previousProvider = image.getStorageProvider();
        String uploadedReplacementKey = null;

        if (file != null && !file.isEmpty()) {
            byte[] content = readImageBytes(file);
            String contentType = resolveContentType(file);
            String newKey = buildS3Key(
                image.getProduct().getId(),
                image.getVariant() == null ? null : image.getVariant().getId(),
                file.getOriginalFilename()
            );
            String publicUrl = storageService.upload(newKey, content, contentType);

            try {
                image.setImageUrl(publicUrl);
                image.setStorageProvider(StorageProvider.S3);
                image.setStorageBucket(storageService.getBucket());
                image.setStorageRegion(storageService.getRegion());
                image.setS3Key(newKey);
                image.setMimeType(contentType);
                image.setFileSize(file.getSize());
                image.setChecksumSha256(sha256Hex(content));
                uploadedReplacementKey = newKey;
            } catch (RuntimeException exception) {
                tryDeleteStorageObject(newKey);
                throw exception;
            }
        }

        if (altText != null) {
            image.setAltText(normalizeText(altText));
        }
        if (sortOrder != null) {
            image.setSortOrder(sortOrder);
        }
        if (primaryImage != null) {
            image.setPrimaryImage(primaryImage);
            if (Boolean.TRUE.equals(primaryImage)) {
                clearPrimaryImage(image.getProduct(), image.getVariant(), image.getId());
            }
        }

        ProductImage saved;
        try {
            saved = productImageRepository.save(image);
        } catch (RuntimeException exception) {
            if (uploadedReplacementKey != null) {
                tryDeleteStorageObject(uploadedReplacementKey);
            }
            throw exception;
        }

        if (file != null && !file.isEmpty() && previousProvider == StorageProvider.S3 && previousKey != null
            && !Objects.equals(previousKey, image.getS3Key())) {
            tryDeleteStorageObject(previousKey);
        }

        return toDto(saved);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    @Caching(evict = {
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public void deleteImage(Long imageId) {
        ProductImage image = findImage(imageId);
        image.setStatus(ProductImageStatus.PENDING_DELETE);
        image.setDeletedAt(LocalDateTime.now());
        image.setPrimaryImage(false);
        productImageRepository.save(image);

        if (image.getStorageProvider() == StorageProvider.S3 && image.getS3Key() != null && !image.getS3Key().isBlank()) {
            try {
                storageService.delete(image.getS3Key());
                image.setStatus(ProductImageStatus.DELETED);
                productImageRepository.save(image);
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to delete S3 object for image id {} and key {}", imageId, image.getS3Key(), exception);
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Image marked for deletion but S3 cleanup failed. Please retry cleanup."
                );
            }
        } else {
            image.setStatus(ProductImageStatus.DELETED);
            productImageRepository.save(image);
        }

        ensurePrimaryImageExists(image.getProduct(), image.getVariant());
    }

    public AdminProductImageDto getImage(Long imageId) {
        return toDto(findImage(imageId));
    }

    public List<AdminProductImageDto> listImagesByProduct(Long productId) {
        findProduct(productId);
        return productImageRepository.findByProductIdOrderBySortOrderAsc(productId).stream()
            .filter(image -> image.getStatus() == null || image.getStatus() == ProductImageStatus.ACTIVE)
            .sorted(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
            .map(this::toDto)
            .toList();
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private ProductVariant validateVariant(Product product, Long variantId) {
        if (variantId == null) {
            return null;
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));

        if (!Objects.equals(variant.getProduct().getId(), product.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant does not belong to the selected product");
        }

        return variant;
    }

    private ProductImage findImage(Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));

        if (image.getStatus() == ProductImageStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return image;
    }

    private byte[] readImageBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read image file", exception);
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads are supported");
        }
        return contentType;
    }

    private String buildS3Key(Long productId, Long variantId, String originalFilename) {
        String sanitizedFilename = sanitizeFilename(originalFilename);
        String scope = variantId == null ? "gallery" : "variants/" + variantId;
        return storageService.getProductImagePrefix().replaceAll("^/+|/+$", "")
            + "/" + productId
            + "/" + scope
            + "/" + UUID.randomUUID()
            + "-" + sanitizedFilename;
    }

    private String sanitizeFilename(String originalFilename) {
        String filename = originalFilename == null || originalFilename.isBlank() ? "image.bin" : originalFilename.trim();
        return filename.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void clearPrimaryImage(Product product, ProductVariant variant, Long excludedImageId) {
        for (ProductImage item : product.getImages()) {
            if (item.getStatus() != null && item.getStatus() != ProductImageStatus.ACTIVE) {
                continue;
            }
            boolean sameScope = variant == null
                ? item.getVariant() == null
                : item.getVariant() != null && Objects.equals(item.getVariant().getId(), variant.getId());
            if (!sameScope) {
                continue;
            }
            if (excludedImageId != null && Objects.equals(item.getId(), excludedImageId)) {
                continue;
            }
            item.setPrimaryImage(false);
        }
    }

    private void ensurePrimaryImageExists(Product product, ProductVariant variant) {
        List<ProductImage> scopedImages = product.getImages().stream()
            .filter(item -> item.getStatus() == null || item.getStatus() == ProductImageStatus.ACTIVE)
            .filter(item -> variant == null
                ? item.getVariant() == null
                : item.getVariant() != null && Objects.equals(item.getVariant().getId(), variant.getId()))
            .sorted(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
            .toList();

        if (scopedImages.isEmpty() || scopedImages.stream().anyMatch(ProductImage::getPrimaryImage)) {
            return;
        }

        ProductImage firstImage = scopedImages.get(0);
        firstImage.setPrimaryImage(true);
        productImageRepository.save(firstImage);
    }

    private void tryDeleteStorageObject(String s3Key) {
        try {
            storageService.delete(s3Key);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to delete old S3 object for key {}", s3Key, exception);
        }
    }

    private AdminProductImageDto toDto(ProductImage image) {
        return new AdminProductImageDto(
            image.getId(),
            image.getProduct().getId(),
            image.getVariant() == null ? null : image.getVariant().getId(),
            image.getImageUrl(),
            image.getAltText(),
            image.getSortOrder(),
            image.getPrimaryImage(),
            image.getStorageProvider(),
            image.getStorageBucket(),
            image.getStorageRegion(),
            image.getS3Key(),
            image.getMimeType(),
            image.getFileSize(),
            image.getChecksumSha256(),
            image.getStatus(),
            image.getDeletedAt(),
            image.getCreatedAt(),
            image.getUpdatedAt()
        );
    }
}
