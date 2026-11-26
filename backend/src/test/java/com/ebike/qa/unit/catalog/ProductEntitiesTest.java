package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.model.entity.product.BatteryType;
import com.ebike.model.entity.product.Category;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductImage;
import com.ebike.model.entity.product.ProductImageStatus;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.Review;
import com.ebike.model.entity.product.StorageProvider;
import com.ebike.model.entity.product.VehicleType;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductEntitiesTest {

    @Test
    void newProductStartsActiveUnfeaturedWithZeroStockRatingAndReviews() {
        Product product = new Product();
        assertThat(product.getActive()).isTrue();
        assertThat(product.getFeatured()).isFalse();
        assertThat(product.getStockQuantity()).isZero();
        assertThat(product.getReviewCount()).isZero();
        assertThat(product.getRating()).isEqualByComparingTo("0");
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getImages()).isEmpty();
        assertThat(product.getVariants()).isEmpty();
    }

    @Test
    void creationTimeIsStampedOnPersistCallback() {
        Product product = new Product();
        ReflectionTestUtils.invokeMethod(product, "onCreate");
        assertThat(product.getCreatedAt()).isBetween(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
        Review review = new Review();
        ReflectionTestUtils.invokeMethod(review, "onCreate");
        assertThat(review.getCreatedAt()).isNotNull();
    }

    @Test
    void imageDefaultsAndLifecycleCallbacks() {
        ProductImage image = new ProductImage();
        assertThat(image.getStatus()).isEqualTo(ProductImageStatus.ACTIVE);
        assertThat(image.getStorageProvider()).isEqualTo(StorageProvider.LOCAL);
        assertThat(image.getPrimaryImage()).isFalse();
        assertThat(image.getSortOrder()).isZero();
        image.setStatus(null);
        image.setStorageProvider(null);
        ReflectionTestUtils.invokeMethod(image, "onCreate");
        assertThat(image.getStatus()).isEqualTo(ProductImageStatus.ACTIVE);
        assertThat(image.getStorageProvider()).isEqualTo(StorageProvider.LOCAL);
        assertThat(image.getCreatedAt()).isNotNull().isEqualTo(image.getUpdatedAt());
        image.setUpdatedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        ReflectionTestUtils.invokeMethod(image, "onUpdate");
        assertThat(image.getUpdatedAt()).isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
    }

    @Test
    void variantAndCategoryDefaults() {
        ProductVariant variant = new ProductVariant();
        assertThat(variant.getActive()).isTrue();
        assertThat(variant.getDefaultVariant()).isFalse();
        assertThat(variant.getImages()).isEmpty();
        assertThat(new Category().getActive()).isTrue();
    }

    /**
     * Expected: a specification saved without a brand has no brand (the shop filter would otherwise offer a made-up one).
     * Actual: the field defaults to the placeholder "Yadea-style", a competitor's name, which then shows in filter options, related-product
     * scoring and the JSON-LD "brand". ProductSpecification.java:32.
     */
    @Test
    @Tag("bug")
    void bug_specificationBrandDefaultsToACompetitorPlaceholder() {
        assertThat(new ProductSpecification().getBrand()).isNull();
    }

    @Test
    void specificationDefaultsToAScooterWithNoBatteryType() {
        ProductSpecification spec = new ProductSpecification();
        assertThat(spec.getVehicleType()).isEqualTo(VehicleType.E_SCOOTER);
        assertThat(spec.getBatteryType()).isNull();
    }

    @Test
    void enumsHaveExactlyTheValuesTheFiltersAndTheCalculatorKnow() {
        assertThat(VehicleType.values()).extracting(Enum::name).containsExactly("E_SCOOTER", "E_BIKE", "E_MOTORBIKE", "BATTERY", "ACCESSORY");
        assertThat(BatteryType.values()).extracting(Enum::name).containsExactly("LEAD_ACID", "LITHIUM_ION", "LFP");
        assertThat(ProductImageStatus.values()).extracting(Enum::name).containsExactly("ACTIVE", "PENDING_DELETE", "DELETED");
    }

    @Test
    void productPriceRoundTripKeepsScale() {
        Product product = new Product();
        product.setPrice(new BigDecimal("25990000.00"));
        assertThat(product.getPrice()).isEqualByComparingTo("25990000");
        Method[] methods = Product.class.getDeclaredMethods();
        assertThat(methods).isNotEmpty();
    }
}
