package com.ebike.productModule.service;

import com.ebike.productModule.dto.response.CategorySummaryDto;
import com.ebike.productModule.dto.response.ProductDetailDto;
import com.ebike.productModule.dto.response.ProductFilterOptionsDto;
import com.ebike.productModule.dto.response.ProductSpecificationDto;
import com.ebike.productModule.dto.response.ProductSummaryDto;
import com.ebike.productModule.dto.response.ProductVariantDto;
import com.ebike.productModule.entity.Product;
import com.ebike.productModule.entity.ProductImage;
import com.ebike.productModule.entity.ProductImageStatus;
import com.ebike.productModule.entity.ProductSpecification;
import com.ebike.productModule.entity.ProductVariant;
import com.ebike.productModule.entity.BatteryType;
import com.ebike.productModule.entity.VehicleType;
import com.ebike.productModule.repository.ProductRepository;
import com.ebike.config.RedisCacheConfiguration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final int DEFAULT_RELATED_PRODUCT_LIMIT = 4;
    private static final int MAX_RELATED_PRODUCT_LIMIT = 12;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE)
    public List<ProductSummaryDto> getProducts(
        String query,
        Integer categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String brand,
        String vehicleType,
        String batteryType,
        BigDecimal minRangeKm,
        BigDecimal maxRangeKm,
        Boolean inStock,
        String sortBy,
        String sortDir
    ) {
        Specification<Product> specification = Specification.where(isActive());

        if (query != null && !query.trim().isEmpty()) {
            specification = specification.and(nameOrDescriptionContains(query.trim()));
        }
        if (categoryId != null) {
            specification = specification.and(hasCategoryId(categoryId));
        }
        if (minPrice != null) {
            specification = specification.and(hasMinPrice(minPrice));
        }
        if (maxPrice != null) {
            specification = specification.and(hasMaxPrice(maxPrice));
        }
        if (brand != null && !brand.trim().isEmpty()) {
            specification = specification.and(hasBrand(brand.trim()));
        }
        if (vehicleType != null && !vehicleType.trim().isEmpty()) {
            specification = specification.and(hasVehicleType(vehicleType.trim()));
        }
        if (batteryType != null && !batteryType.trim().isEmpty()) {
            specification = specification.and(hasBatteryType(batteryType.trim()));
        }
        if (minRangeKm != null) {
            specification = specification.and(hasMinRangeKm(minRangeKm));
        }
        if (maxRangeKm != null) {
            specification = specification.and(hasMaxRangeKm(maxRangeKm));
        }
        if (Boolean.TRUE.equals(inStock)) {
            specification = specification.and(hasStockAvailable());
        }

        List<Product> products = new ArrayList<>(productRepository.findAll(specification));
        Comparator<Product> comparator = resolveSortComparator(sortBy, sortDir);
        if (comparator != null) {
            products.sort(comparator);
        }

        return products.stream()
            .map(this::toSummaryDto)
            .toList();
    }

    public ProductFilterOptionsDto getFilterOptions() {
        List<Product> products = productRepository.findAll(isActive());
        TreeSet<String> brands = new TreeSet<>();
        TreeSet<String> vehicleTypes = new TreeSet<>();
        TreeSet<String> batteryTypes = new TreeSet<>();

        for (Product product : products) {
            ProductSpecification specification = product.getSpecification();
            if (specification == null) {
                continue;
            }
            if (specification.getBrand() != null && !specification.getBrand().isBlank()) {
                brands.add(specification.getBrand().trim());
            }
            if (specification.getVehicleType() != null) {
                vehicleTypes.add(specification.getVehicleType().name());
            }
            if (specification.getBatteryType() != null) {
                batteryTypes.add(specification.getBatteryType().name());
            }
        }

        return new ProductFilterOptionsDto(
            new ArrayList<>(brands),
            new ArrayList<>(vehicleTypes),
            new ArrayList<>(batteryTypes)
        );
    }

    @Cacheable(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, key = "#slug")
    public ProductDetailDto getProductBySlug(String slug) {
        Product product = productRepository.findDetailBySlug(slug)
            .filter(item -> Boolean.TRUE.equals(item.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        return toDetailDto(product);
    }

    public List<ProductSummaryDto> getRelatedProducts(String slug, Integer limit) {
        Product product = productRepository.findBySlug(slug)
            .filter(item -> Boolean.TRUE.equals(item.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        int resolvedLimit = resolveRelatedProductLimit(limit);

        return productRepository.findAll(isActive()).stream()
            .filter(candidate -> !candidate.getId().equals(product.getId()))
            .map(candidate -> new RelatedProductCandidate(candidate, calculateRelatedScore(product, candidate)))
            .filter(candidate -> candidate.score() > 0)
            .sorted(
                Comparator.comparingInt(RelatedProductCandidate::score).reversed()
                    .thenComparing(candidate -> candidate.product().getRating(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(candidate -> candidate.product().getName())
            )
            .limit(resolvedLimit)
            .map(candidate -> toSummaryDto(candidate.product()))
            .toList();
    }

    private Specification<Product> isActive() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("active"));
    }

    private Specification<Product> hasCategoryId(Integer categoryId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("category").get("id"), categoryId);
    }

    private Specification<Product> hasMinPrice(BigDecimal minPrice) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    private Specification<Product> hasMaxPrice(BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    private Specification<Product> nameOrDescriptionContains(String queryText) {
        return (root, query, criteriaBuilder) -> {
            String pattern = "%" + queryText.toLowerCase() + "%";
            return criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
            );
        };
    }

    private Specification<Product> hasBrand(String brand) {
        return (root, query, criteriaBuilder) -> {
            Join<Product, ProductSpecification> specification = root.join("specification", JoinType.INNER);
            return criteriaBuilder.like(
                criteriaBuilder.lower(specification.get("brand")),
                "%" + brand.toLowerCase(Locale.ROOT) + "%"
            );
        };
    }

    private Specification<Product> hasVehicleType(String vehicleType) {
        VehicleType parsedType = parseVehicleType(vehicleType);
        return (root, query, criteriaBuilder) -> {
            Join<Product, ProductSpecification> specification = root.join("specification", JoinType.INNER);
            return criteriaBuilder.equal(specification.get("vehicleType"), parsedType);
        };
    }

    private Specification<Product> hasBatteryType(String batteryType) {
        BatteryType parsedType = parseBatteryType(batteryType);
        return (root, query, criteriaBuilder) -> {
            Join<Product, ProductSpecification> specification = root.join("specification", JoinType.INNER);
            return criteriaBuilder.equal(specification.get("batteryType"), parsedType);
        };
    }

    private Specification<Product> hasMinRangeKm(BigDecimal minRangeKm) {
        return (root, query, criteriaBuilder) -> {
            Join<Product, ProductSpecification> specification = root.join("specification", JoinType.INNER);
            return criteriaBuilder.greaterThanOrEqualTo(specification.get("maxRangeKm"), minRangeKm);
        };
    }

    private Specification<Product> hasMaxRangeKm(BigDecimal maxRangeKm) {
        return (root, query, criteriaBuilder) -> {
            Join<Product, ProductSpecification> specification = root.join("specification", JoinType.INNER);
            return criteriaBuilder.lessThanOrEqualTo(specification.get("maxRangeKm"), maxRangeKm);
        };
    }

    private Specification<Product> hasStockAvailable() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThan(root.get("stockQuantity"), 0);
    }

    private VehicleType parseVehicleType(String rawValue) {
        try {
            return VehicleType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vehicleType filter");
        }
    }

    private BatteryType parseBatteryType(String rawValue) {
        try {
            return BatteryType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid batteryType filter");
        }
    }

    private Comparator<Product> resolveSortComparator(String sortBy, String sortDir) {
        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }

        boolean ascending = !"desc".equalsIgnoreCase(sortDir);
        return switch (sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "price" -> ascending
                ? Comparator.comparing(this::effectivePrice)
                : Comparator.comparing(this::effectivePrice).reversed();
            case "name" -> {
                Comparator<Product> byName = Comparator.comparing(
                    (Product product) -> product.getName().toLowerCase(Locale.ROOT)
                );
                yield ascending ? byName : byName.reversed();
            }
            case "newest" -> ascending
                ? Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> null;
        };
    }

    private BigDecimal effectivePrice(Product product) {
        return product.getDiscountPrice() == null ? product.getPrice() : product.getDiscountPrice();
    }

    private int resolveRelatedProductLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RELATED_PRODUCT_LIMIT;
        }
        return Math.min(limit, MAX_RELATED_PRODUCT_LIMIT);
    }

    private int calculateRelatedScore(Product product, Product candidate) {
        int score = 0;

        if (product.getCategory() != null && candidate.getCategory() != null
            && product.getCategory().getId().equals(candidate.getCategory().getId())) {
            score += 5;
        }

        ProductSpecification productSpecification = product.getSpecification();
        ProductSpecification candidateSpecification = candidate.getSpecification();
        if (productSpecification != null && candidateSpecification != null) {
            if (sameText(productSpecification.getBrand(), candidateSpecification.getBrand())) {
                score += 3;
            }
            if (productSpecification.getVehicleType() != null
                && productSpecification.getVehicleType() == candidateSpecification.getVehicleType()) {
                score += 3;
            }
        }

        if (isPriceNear(product.getDiscountPrice() == null ? product.getPrice() : product.getDiscountPrice(),
            candidate.getDiscountPrice() == null ? candidate.getPrice() : candidate.getDiscountPrice())) {
            score += 2;
        }

        return score;
    }

    private boolean sameText(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return first.trim().toLowerCase(Locale.ROOT).equals(second.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isPriceNear(BigDecimal productPrice, BigDecimal candidatePrice) {
        if (productPrice == null || candidatePrice == null || productPrice.signum() <= 0) {
            return false;
        }
        BigDecimal lowerBound = productPrice.multiply(new BigDecimal("0.70"));
        BigDecimal upperBound = productPrice.multiply(new BigDecimal("1.30"));
        return candidatePrice.compareTo(lowerBound) >= 0 && candidatePrice.compareTo(upperBound) <= 0;
    }

    private record RelatedProductCandidate(Product product, int score) {
    }

    public ProductSummaryDto toSummaryDto(Product product) {
        return new ProductSummaryDto(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getPrice(),
            product.getDiscountPrice(),
            product.getRating(),
            product.getReviewCount(),
            product.getStockQuantity(),
            product.getFeatured(),
            new CategorySummaryDto(
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getSlug()
            ),
            mapImages(product)
        );
    }

    private ProductDetailDto toDetailDto(Product product) {
        return new ProductDetailDto(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getPrice(),
            product.getDiscountPrice(),
            product.getRating(),
            product.getReviewCount(),
            product.getStockQuantity(),
            product.getFeatured(),
            new CategorySummaryDto(
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getSlug()
            ),
            mapSpecification(product.getSpecification()),
            product.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getId))
                .map(this::mapVariant)
                .toList(),
            mapImages(product)
        );
    }

    private ProductSpecificationDto mapSpecification(ProductSpecification specification) {
        if (specification == null) {
            return null;
        }
        return new ProductSpecificationDto(
            specification.getModelCode(),
            specification.getBrand(),
            specification.getVehicleType() == null ? null : specification.getVehicleType().name(),
            specification.getBatteryType() == null ? null : specification.getBatteryType().name(),
            specification.getBatteryCapacityAh(),
            specification.getMaxSpeedKmh(),
            specification.getMaxRangeKm(),
            specification.getMotorPowerWatts(),
            specification.getBrakeType() == null ? null : specification.getBrakeType().name(),
            specification.getDriveType() == null ? null : specification.getDriveType().name(),
            specification.getWarrantyMonths()
        );
    }

    private ProductVariantDto mapVariant(ProductVariant variant) {
        return new ProductVariantDto(
            variant.getId(),
            variant.getSku(),
            variant.getVariantName(),
            variant.getColorName(),
            variant.getColorHex(),
            mapVariantImage(variant),
            variant.getBatteryCapacityAh(),
            variant.getAdditionalPrice(),
            variant.getStockQuantity(),
            variant.getDefaultVariant()
        );
    }

    private String mapVariantImage(ProductVariant variant) {
        return variant.getImages().stream()
            .filter(this::isActiveImage)
            .sorted(Comparator.comparing(ProductImage::getSortOrder))
            .map(ProductImage::getImageUrl)
            .findFirst()
            .orElse(null);
    }

    private List<String> mapImages(Product product) {
        return product.getImages().stream()
            .filter(this::isActiveImage)
            .sorted(Comparator.comparing(ProductImage::getSortOrder))
            .map(ProductImage::getImageUrl)
            .toList();
    }

    private boolean isActiveImage(ProductImage image) {
        return image.getStatus() == null || image.getStatus() == ProductImageStatus.ACTIVE;
    }
}
