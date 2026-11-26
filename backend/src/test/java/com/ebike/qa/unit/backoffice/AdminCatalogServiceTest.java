package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.admin.request.CatalogCategoryRequest;
import com.ebike.dto.admin.request.CatalogProductRequest;
import com.ebike.model.entity.product.Category;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductImage;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.StorageProvider;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.repository.product.CategoryRepository;
import com.ebike.repository.product.ProductImageRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.product.ProductSpecificationRepository;
import com.ebike.service.admin.AdminCatalogService;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.product.ProductImageStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminCatalogServiceTest {

    ProductRepository products = mock(ProductRepository.class);
    CategoryRepository categories = mock(CategoryRepository.class);
    ProductSpecificationRepository specs = mock(ProductSpecificationRepository.class);
    ProductImageRepository images = mock(ProductImageRepository.class);
    ProductImageStorageService storage = mock(ProductImageStorageService.class);
    AuditLogService audit = mock(AuditLogService.class);
    AdminCatalogService service;
    Category category;

    @BeforeEach
    void setUp() {
        service = new AdminCatalogService(products, categories, specs, images, storage, audit);
        category = new Category();
        category.setId(1);
        category.setName("Xe điện");
        category.setActive(true);
        when(categories.findById(1)).thenReturn(Optional.of(category));
        when(products.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId(50L);
            }
            when(products.findById(p.getId())).thenReturn(Optional.of(p));
            return p;
        });
        when(specs.save(any(ProductSpecification.class))).thenAnswer(i -> i.getArgument(0));
        when(categories.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    private static CatalogProductRequest req(String name, String slug, Integer cat, String price, String discount) {
        return new CatalogProductRequest(name, slug, "desc", cat, new BigDecimal(price), discount == null ? null : new BigDecimal(discount),
            true, null, null, null, null, null, null, null, null, null, null, null);
    }

    // ------------------------------------------------------------------ products

    @Test
    void createProduct_happyPath_startsWithZeroStock_defaultsBrandAndVehicleType_activeByDefault() {
        var detail = service.createProduct(req("  DIBAO Amy ", null, 1, "17700000", "16000000"));
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(products).save(saved.capture());
        Product p = saved.getValue();
        assertThat(p.getName()).isEqualTo("DIBAO Amy");
        assertThat(p.getSlug()).isEqualTo("dibao-amy");
        assertThat(p.getStockQuantity()).isZero();
        assertThat(p.getActive()).isTrue();
        assertThat(p.getFeatured()).isTrue();
        assertThat(p.getPrice()).isEqualByComparingTo("17700000");
        assertThat(p.getDiscountPrice()).isEqualByComparingTo("16000000");
        ArgumentCaptor<ProductSpecification> spec = ArgumentCaptor.forClass(ProductSpecification.class);
        verify(specs).save(spec.capture());
        assertThat(spec.getValue().getBrand()).isEqualTo("KINETIC");
        assertThat(spec.getValue().getVehicleType()).isEqualTo(VehicleType.E_SCOOTER);
        assertThat(detail.id()).isEqualTo(50L);
        verify(audit).record("tạo sản phẩm", "DIBAO Amy");
    }

    @Test
    void createProduct_duplicateSlug_is409_nothingSaved() {
        when(products.existsBySlug("dibao-amy")).thenReturn(true);
        assertThatThrownBy(() -> service.createProduct(req("DIBAO Amy", null, 1, "1000", null))).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(products, never()).save(any());
        verify(specs, never()).save(any());
    }

    @Test
    void createProduct_unknownCategory_is400() {
        assertThatThrownBy(() -> service.createProduct(req("DIBAO Amy", null, 99, "1000", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void createProduct_validationFailureHappensBeforeAnyLookup() {
        assertThatThrownBy(() -> service.createProduct(req("DIBAO Amy", null, 1, "0", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(products, never()).existsBySlug(any());
        verify(products, never()).save(any());
    }

    @Test
    void createProduct_activeFalseIsRespected() {
        CatalogProductRequest r = new CatalogProductRequest("DIBAO Amy", null, null, 1, new BigDecimal("1000"), null, false, false,
            null, null, null, null, null, null, null, null, null, null);
        service.createProduct(r);
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(products).save(saved.capture());
        assertThat(saved.getValue().getActive()).isFalse();
        assertThat(saved.getValue().getFeatured()).isFalse();
    }

    @Test
    void updateProduct_neverTouchesStock_andChecksSlugAgainstOtherProducts() {
        Product p = Fx.product(7L, "Old");
        p.setStockQuantity(33);
        p.setCategory(category);
        when(products.findById(7L)).thenReturn(Optional.of(p));
        when(specs.findByProductId(7L)).thenReturn(Optional.empty());
        service.updateProduct(7L, req("New Name", "new-name", 1, "2000", null));
        assertThat(p.getStockQuantity()).isEqualTo(33);
        assertThat(p.getName()).isEqualTo("New Name");
        verify(products).existsBySlugAndIdNot("new-name", 7L);
        verify(audit).record("cập nhật sản phẩm", "New Name");
    }

    @Test
    void updateProduct_slugTakenByAnother_is409_unknownProduct_is404() {
        Product p = Fx.product(7L, "Old");
        p.setCategory(category);
        when(products.findById(7L)).thenReturn(Optional.of(p));
        when(products.existsBySlugAndIdNot("taken", 7L)).thenReturn(true);
        assertThatThrownBy(() -> service.updateProduct(7L, req("Name", "taken", 1, "2000", null))).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.updateProduct(8L, req("Name", "x", 1, "2000", null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        assertThat(p.getName()).isEqualTo("Old");
    }

    @Test
    void deleteProduct_withOrders_is409_withTestRides_is409() {
        Product p = Fx.product(7L, "Amy");
        when(products.findById(7L)).thenReturn(Optional.of(p));
        when(products.countOrderItems(7L)).thenReturn(2L);
        assertThatThrownBy(() -> service.deleteProduct(7L)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        when(products.countOrderItems(7L)).thenReturn(0L);
        when(products.countTestRides(7L)).thenReturn(1L);
        assertThatThrownBy(() -> service.deleteProduct(7L)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(products, never()).delete(any(Product.class));
    }

    @Test
    void deleteProduct_unused_deletesRowAndAuditsAndRemovesOnlyS3Photos() {
        Product p = Fx.product(7L, "Amy");
        when(products.findById(7L)).thenReturn(Optional.of(p));
        ProductImage s3 = new ProductImage();
        s3.setStorageProvider(StorageProvider.S3);
        ReflectionKey.set(s3, "k1");
        ProductImage local = new ProductImage();
        local.setStorageProvider(StorageProvider.LOCAL);
        ProductImage blank = new ProductImage();
        blank.setStorageProvider(StorageProvider.S3);
        ReflectionKey.set(blank, " ");
        when(images.findByProductIdOrderBySortOrderAsc(7L)).thenReturn(List.of(s3, local, blank));
        service.deleteProduct(7L);
        verify(products).delete(p);
        verify(audit).record("xóa sản phẩm", "Amy");
        verify(storage).delete("k1");
        verify(storage, org.mockito.Mockito.times(1)).delete(any());
    }

    @Test
    void deleteProduct_s3Failure_doesNotUndoTheDelete() {
        Product p = Fx.product(7L, "Amy");
        when(products.findById(7L)).thenReturn(Optional.of(p));
        ProductImage s3 = new ProductImage();
        s3.setStorageProvider(StorageProvider.S3);
        ReflectionKey.set(s3, "k1");
        when(images.findByProductIdOrderBySortOrderAsc(7L)).thenReturn(List.of(s3));
        org.mockito.Mockito.doThrow(new RuntimeException("s3 down")).when(storage).delete("k1");
        service.deleteProduct(7L);
        verify(products).delete(p);
    }

    @Test
    void deleteProduct_orderArrivesAtTheLastMoment_is409_noAuditNoS3() {
        Product p = Fx.product(7L, "Amy");
        when(products.findById(7L)).thenReturn(Optional.of(p));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk")).when(products).flush();
        assertThatThrownBy(() -> service.deleteProduct(7L)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(audit, never()).record(any(), any());
        verify(storage, never()).delete(any());
    }

    @Test
    void getProduct_unknown_is404() {
        assertThatThrownBy(() -> service.getProduct(404L)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------------ categories

    private static CatalogCategoryRequest cat(String name, String slug) {
        return new CatalogCategoryRequest(name, slug, null, null);
    }

    @Test
    void createCategory_happy_slugFromName_activeByDefault() {
        var row = service.createCategory(cat(" Xe đạp điện ", null));
        assertThat(row.name()).isEqualTo("Xe đạp điện");
        assertThat(row.slug()).isEqualTo("xe-dap-dien");
        assertThat(row.active()).isTrue();
        assertThat(row.productCount()).isZero();
        verify(audit).record("tạo loại sản phẩm", "Xe đạp điện");
    }

    @Test
    void createCategory_duplicateNameCaseInsensitive_or_slug_is409() {
        when(categories.existsByNameIgnoreCase("xe điện")).thenReturn(true);
        assertThatThrownBy(() -> service.createCategory(cat("xe điện", null))).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        when(categories.existsBySlug("batteries")).thenReturn(true);
        assertThatThrownBy(() -> service.createCategory(cat("Pin", "batteries"))).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(categories, never()).save(any());
    }

    @Test
    void updateCategory_checksNameAndSlugAgainstOthers() {
        when(categories.existsByNameIgnoreCaseAndIdNot("Pin", 1)).thenReturn(true);
        assertThatThrownBy(() -> service.updateCategory(1, cat("Pin", null))).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        when(categories.existsBySlugAndIdNot("pin", 1)).thenReturn(true);
        assertThatThrownBy(() -> service.updateCategory(1, cat("Pin 2", "pin"))).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.updateCategory(9, cat("Pin 3", null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        assertThat(category.getName()).isEqualTo("Xe điện");
    }

    @Test
    void deleteCategory_withProducts_is409_empty_isDeleted_raceIs409() {
        when(categories.countProducts(1)).thenReturn(3L);
        assertThatThrownBy(() -> service.deleteCategory(1)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(categories, never()).delete(any());
        when(categories.countProducts(1)).thenReturn(0L);
        service.deleteCategory(1);
        verify(categories).delete(category);
        verify(audit).record("xóa loại sản phẩm", "Xe điện");
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk")).when(categories).flush();
        assertThatThrownBy(() -> service.deleteCategory(1)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void deleteCategory_unknown_is404() {
        assertThatThrownBy(() -> service.deleteCategory(77)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        verify(products, never()).countOrderItems(anyLong());
        verify(categories, never()).countProducts(eq(77));
    }

    /** Tiny helper: ProductImage keeps the S3 key in a field with a setter named setS3Key or similar. */
    static final class ReflectionKey {
        static void set(ProductImage image, String key) {
            org.springframework.test.util.ReflectionTestUtils.setField(image, "s3Key", key);
        }
    }
}
