package com.ebike.qa.unit.catalog;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.BatteryType;
import com.ebike.model.entity.product.Category;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductImage;
import com.ebike.model.entity.product.ProductImageStatus;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders shared by the catalog unit tests (plain objects, no persistence). */
final class Fixtures {

    private Fixtures() {
    }

    static Category category(int id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(name.toLowerCase().replace(' ', '-'));
        return category;
    }

    static Product product(long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSlug(name.toLowerCase().replace(' ', '-'));
        product.setDescription("desc " + name);
        product.setPrice(new BigDecimal(price));
        product.setCategory(category(1, "Xe dien"));
        product.setActive(true);
        product.setStockQuantity(5);
        product.setRating(BigDecimal.ZERO);
        product.setReviewCount(0);
        product.setFeatured(false);
        return product;
    }

    static ProductSpecification spec(Product product, VehicleType type, String brand) {
        ProductSpecification spec = new ProductSpecification();
        spec.setProduct(product);
        spec.setVehicleType(type);
        spec.setBrand(brand);
        product.setSpecification(spec);
        return spec;
    }

    static ProductSpecification spec(Product product, VehicleType type, String brand, BatteryType battery) {
        ProductSpecification spec = spec(product, type, brand);
        spec.setBatteryType(battery);
        return spec;
    }

    static ProductImage image(Product product, long id, String url, int sortOrder) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setProduct(product);
        image.setImageUrl(url);
        image.setSortOrder(sortOrder);
        image.setStatus(ProductImageStatus.ACTIVE);
        product.getImages().add(image);
        return image;
    }

    static ProductVariant variant(Product product, long id, String sku, boolean isDefault) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setVariantName("Variant " + sku);
        variant.setColorName("Color " + sku);
        variant.setDefaultVariant(isDefault);
        variant.setActive(true);
        variant.setStockQuantity(3);
        variant.setImages(new LinkedHashSet<>());
        product.getVariants().add(variant);
        return variant;
    }

    static ProductImage variantImage(Product product, ProductVariant variant, long id, String url, int sortOrder) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setProduct(product);
        image.setVariant(variant);
        image.setImageUrl(url);
        image.setSortOrder(sortOrder);
        image.setStatus(ProductImageStatus.ACTIVE);
        variant.getImages().add(image);
        product.getImages().add(image);
        return image;
    }

    static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFirstName("First" + id);
        user.setLastName("Last" + id);
        return user;
    }

    static void createdAt(Product product, LocalDateTime time) {
        ReflectionTestUtils.setField(product, "createdAt", time);
    }
}
