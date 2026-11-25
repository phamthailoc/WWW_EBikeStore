package com.ebike.service.admin;

import com.ebike.config.CacheNames;
import com.ebike.dto.admin.request.CatalogCategoryRequest;
import com.ebike.dto.admin.request.CatalogProductRequest;
import com.ebike.dto.admin.response.CatalogCategoryRow;
import com.ebike.dto.admin.response.CatalogProductDetail;
import com.ebike.dto.admin.response.CatalogProductRow;
import com.ebike.model.entity.product.BatteryType;
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
import com.ebike.service.product.ProductImageStorageService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The admin catalogue: bikes and their categories (list, detail, create, update, delete). Stock never changes here (receipts and orders own it).
 * A bike may be deleted only while no order and no test ride mentions it; a category only while it holds no bike. Everything is validated by
 * {@link CatalogRules}, not by the database.
 */
@Service
@Transactional
public class AdminCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AdminCatalogService.class);
    private static final String DEFAULT_BRAND = "KINETIC";

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductSpecificationRepository specifications;
    private final ProductImageRepository images;
    private final ProductImageStorageService storage;
    private final AuditLogService auditLog;

    public AdminCatalogService(
        ProductRepository products,
        CategoryRepository categories,
        ProductSpecificationRepository specifications,
        ProductImageRepository images,
        ProductImageStorageService storage,
        AuditLogService auditLog
    ) {
        this.products = products;
        this.categories = categories;
        this.specifications = specifications;
        this.images = images;
        this.storage = storage;
        this.auditLog = auditLog;
    }

    // ---- bikes ----

    @Transactional(readOnly = true)
    public List<CatalogProductRow> listProducts(String search, Integer categoryId) {
        Map<Long, Long> orderCounts = new HashMap<>();
        for (Object[] row : products.orderItemCounts()) {
            orderCounts.put((Long) row[0], (Long) row[1]);
        }
        return products.searchCatalog(like(search), categoryId).stream().map(product -> {
            long orders = orderCounts.getOrDefault(product.getId(), 0L);
            return new CatalogProductRow(product.getId(), product.getName(), product.getSlug(), product.getCategory().getId(),
                product.getCategory().getName(), product.getPrice(), product.getDiscountPrice(), product.getStockQuantity(),
                Boolean.TRUE.equals(product.getFeatured()), Boolean.TRUE.equals(product.getActive()), orders, orders == 0 && products.countTestRides(product.getId()) == 0);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CatalogProductDetail getProduct(Long id) {
        return toDetail(find(id));
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public CatalogProductDetail createProduct(CatalogProductRequest request) {
        CatalogRules.checkProduct(request);
        String name = CatalogRules.requiredName(request.name(), CatalogRules.MAX_NAME, "Tên sản phẩm");
        String slug = CatalogRules.slugFor(request.slug(), name, CatalogRules.MAX_NAME);
        if (products.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn \"" + slug + "\" đã được sản phẩm khác dùng.");
        }
        Product product = new Product();
        product.setStockQuantity(0);
        apply(product, request, name, slug);
        Product saved = products.save(product);
        specifications.save(applySpecification(new ProductSpecification(), saved, request));
        auditLog.record("tạo sản phẩm", saved.getName());
        return toDetail(find(saved.getId()));
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public CatalogProductDetail updateProduct(Long id, CatalogProductRequest request) {
        CatalogRules.checkProduct(request);
        Product product = find(id);
        String name = CatalogRules.requiredName(request.name(), CatalogRules.MAX_NAME, "Tên sản phẩm");
        String slug = CatalogRules.slugFor(request.slug(), name, CatalogRules.MAX_NAME);
        if (products.existsBySlugAndIdNot(slug, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn \"" + slug + "\" đã được sản phẩm khác dùng.");
        }
        apply(product, request, name, slug);
        products.save(product);
        ProductSpecification specification = specifications.findByProductId(id).orElseGet(ProductSpecification::new);
        specifications.save(applySpecification(specification, product, request));
        auditLog.record("cập nhật sản phẩm", product.getName());
        return toDetail(find(id));
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public void deleteProduct(Long id) {
        Product product = find(id);
        long orders = products.countOrderItems(id);
        if (orders > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Sản phẩm đã có trong " + orders + " đơn hàng nên không thể xóa. Hãy bỏ chọn \"Đang bán\" để ngừng bán.");
        }
        if (products.countTestRides(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm đã có lịch lái thử nên không thể xóa. Hãy bỏ chọn \"Đang bán\" để ngừng bán.");
        }
        // the rows of colours, photos, reviews, favourites and stock history go with the bike (the foreign keys cascade); photos kept on S3 are removed too
        for (ProductImage image : images.findByProductIdOrderBySortOrderAsc(id)) {
            if (image.getStorageProvider() == StorageProvider.S3 && image.getS3Key() != null && !image.getS3Key().isBlank()) {
                try {
                    storage.delete(image.getS3Key());
                } catch (RuntimeException exception) {
                    log.warn("Could not delete the S3 object {} of deleted product id {}", image.getS3Key(), id, exception);
                }
            }
        }
        String name = product.getName();
        products.delete(product);
        auditLog.record("xóa sản phẩm", name);
    }

    // ---- categories ----

    @Transactional(readOnly = true)
    public List<CatalogCategoryRow> listCategories(String search) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : categories.productCounts()) {
            counts.put((Integer) row[0], (Long) row[1]);
        }
        return categories.search(like(search)).stream().map(category -> row(category, counts.getOrDefault(category.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public CatalogCategoryRow getCategory(Integer id) {
        Category category = findCategory(id);
        return row(category, categories.countProducts(id));
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public CatalogCategoryRow createCategory(CatalogCategoryRequest request) {
        CatalogRules.checkCategory(request);
        String name = CatalogRules.requiredName(request.name(), CatalogRules.MAX_CATEGORY_NAME, "Tên loại");
        String slug = CatalogRules.slugFor(request.slug(), name, CatalogRules.MAX_CATEGORY_NAME);
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã có loại sản phẩm tên \"" + name + "\".");
        }
        if (categories.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn \"" + slug + "\" đã được loại khác dùng.");
        }
        Category category = new Category();
        fill(category, request, name, slug);
        Category saved = categories.save(category);
        auditLog.record("tạo loại sản phẩm", saved.getName());
        return row(saved, 0);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public CatalogCategoryRow updateCategory(Integer id, CatalogCategoryRequest request) {
        CatalogRules.checkCategory(request);
        Category category = findCategory(id);
        String name = CatalogRules.requiredName(request.name(), CatalogRules.MAX_CATEGORY_NAME, "Tên loại");
        String slug = CatalogRules.slugFor(request.slug(), name, CatalogRules.MAX_CATEGORY_NAME);
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã có loại sản phẩm tên \"" + name + "\".");
        }
        if (categories.existsBySlugAndIdNot(slug, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn \"" + slug + "\" đã được loại khác dùng.");
        }
        fill(category, request, name, slug);
        categories.save(category);
        auditLog.record("cập nhật loại sản phẩm", category.getName());
        return row(category, categories.countProducts(id));
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public void deleteCategory(Integer id) {
        Category category = findCategory(id);
        long count = categories.countProducts(id);
        if (count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loại này còn " + count + " sản phẩm nên không thể xóa. Hãy chuyển sản phẩm sang loại khác trước.");
        }
        String name = category.getName();
        categories.delete(category);
        auditLog.record("xóa loại sản phẩm", name);
    }

    // ---- helpers ----

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
    }

    private Category findCategory(Integer id) {
        return categories.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy loại sản phẩm."));
    }

    private static String like(String search) {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private void apply(Product product, CatalogProductRequest request, String name, String slug) {
        Category category = categories.findById(request.categoryId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại sản phẩm không tồn tại."));
        product.setCategory(category);
        product.setName(name);
        product.setSlug(slug);
        product.setDescription(CatalogRules.optionalText(request.description(), CatalogRules.MAX_DESCRIPTION, "Mô tả"));
        product.setPrice(request.price());
        product.setDiscountPrice(request.discountPrice());
        product.setFeatured(Boolean.TRUE.equals(request.featured()));
        product.setActive(request.active() == null || request.active());
    }

    private ProductSpecification applySpecification(ProductSpecification specification, Product product, CatalogProductRequest request) {
        specification.setProduct(product);
        String brand = CatalogRules.optionalText(request.brand(), 100, "Thương hiệu");
        specification.setBrand(brand == null ? DEFAULT_BRAND : brand);
        specification.setModelCode(CatalogRules.optionalText(request.modelCode(), 100, "Mã mẫu"));
        VehicleType vehicleType = CatalogRules.enumValue(VehicleType.class, request.vehicleType(), "Loại xe");
        specification.setVehicleType(vehicleType == null ? VehicleType.E_SCOOTER : vehicleType);
        BatteryType batteryType = CatalogRules.enumValue(BatteryType.class, request.batteryType(), "Loại pin");
        specification.setBatteryType(batteryType);
        specification.setBatteryCapacityAh(request.batteryCapacityAh());
        specification.setBatteryVoltageV(request.batteryVoltageV());
        specification.setMaxSpeedKmh(request.maxSpeedKmh());
        specification.setMaxRangeKm(request.maxRangeKm());
        specification.setMotorPowerWatts(request.motorPowerWatts());
        specification.setWarrantyMonths(request.warrantyMonths());
        return specification;
    }

    private void fill(Category category, CatalogCategoryRequest request, String name, String slug) {
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(CatalogRules.optionalText(request.description(), CatalogRules.MAX_DESCRIPTION, "Mô tả"));
        category.setActive(request.active() == null || request.active());
    }

    private CatalogCategoryRow row(Category category, long productCount) {
        return new CatalogCategoryRow(category.getId(), category.getName(), category.getSlug(), category.getDescription(),
            Boolean.TRUE.equals(category.getActive()), productCount, productCount == 0);
    }

    private CatalogProductDetail toDetail(Product product) {
        ProductSpecification spec = specifications.findByProductId(product.getId()).orElse(null);
        long orders = products.countOrderItems(product.getId());
        boolean canDelete = orders == 0 && products.countTestRides(product.getId()) == 0;
        return new CatalogProductDetail(product.getId(), product.getName(), product.getSlug(), product.getDescription(), product.getCategory().getId(),
            product.getCategory().getName(), product.getPrice(), product.getDiscountPrice(), product.getStockQuantity(),
            Boolean.TRUE.equals(product.getFeatured()), Boolean.TRUE.equals(product.getActive()),
            spec == null ? null : spec.getBrand(), spec == null ? null : spec.getModelCode(),
            spec == null || spec.getVehicleType() == null ? null : spec.getVehicleType().name(),
            spec == null || spec.getBatteryType() == null ? null : spec.getBatteryType().name(),
            spec == null ? null : spec.getBatteryCapacityAh(), spec == null ? null : spec.getBatteryVoltageV(),
            spec == null ? null : spec.getMaxSpeedKmh(), spec == null ? null : spec.getMaxRangeKm(),
            spec == null ? null : spec.getMotorPowerWatts(), spec == null ? null : spec.getWarrantyMonths(), orders, canDelete);
    }
}
