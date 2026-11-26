package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.config.CacheNames;
import com.ebike.dto.product.response.ProductDetailDto;
import com.ebike.dto.product.response.ProductFilterOptionsDto;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.model.entity.product.BatteryType;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductImage;
import com.ebike.model.entity.product.ProductImageStatus;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.product.ProductService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ProductServiceTest {

    private ProductRepository repository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        service = new ProductService(repository);
    }

    private void stockCatalog(Product... products) {
        when(repository.findAll(ArgumentMatchers.<Specification<Product>>any())).thenReturn(new ArrayList<>(List.of(products)));
    }

    @SuppressWarnings("unchecked")
    private Specification<Product> capturedSpec() {
        ArgumentCaptor<Specification<Product>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture());
        return captor.getValue();
    }

    private List<ProductSummaryDto> list(String query, Integer cat, String min, String max, String brand, String vehicle,
                                         String battery, String minRange, String maxRange, Boolean inStock, String sortBy, String sortDir) {
        return service.getProducts(query, cat, bd(min), bd(max), brand, vehicle, battery, bd(minRange), bd(maxRange), inStock, sortBy, sortDir);
    }

    private static BigDecimal bd(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private List<String> names(List<ProductSummaryDto> list) {
        return list.stream().map(ProductSummaryDto::name).toList();
    }

    // ------------------------------------------------------------------ filters (predicate built)

    @Test
    void noFiltersOnlyRestrictsToActiveProducts() {
        stockCatalog();
        list(null, null, null, null, null, null, null, null, null, null, null, null);
        assertThat(new SpecRecorder().describe(capturedSpec())).isEqualTo("isTrue(active)");
    }

    @Test
    void blankAndWhitespaceFiltersAreIgnored() {
        stockCatalog();
        list("   ", null, null, null, "  ", "   ", "\t", null, null, false, "  ", null);
        assertThat(new SpecRecorder().describe(capturedSpec())).isEqualTo("isTrue(active)");
    }

    @Test
    void queryIsTrimmedAndLowerCasedAndSearchesNameAndDescription() {
        stockCatalog();
        list("  Xe ĐIỆN Nhanh  ", null, null, null, null, null, null, null, null, null, null, null);
        String predicate = new SpecRecorder().describe(capturedSpec());
        assertThat(predicate).contains("like(lower(name),%xe điện nhanh%)").contains("like(lower(description),%xe điện nhanh%)");
    }

    @Test
    void categoryPriceBrandRangeAndStockFiltersAreAllAndedTogether() {
        stockCatalog();
        list(null, 7, "1000000", "5000000", "VinFast", null, null, "30", "90", true, null, null);
        String predicate = new SpecRecorder().describe(capturedSpec());
        assertThat(predicate)
            .contains("equal(category.id,7)")
            .contains("greaterThanOrEqualTo(price,1000000)")
            .contains("lessThanOrEqualTo(price,5000000)")
            .contains("like(lower(specification.brand),%vinfast%)")
            .contains("greaterThanOrEqualTo(specification.maxRangeKm,30)")
            .contains("lessThanOrEqualTo(specification.maxRangeKm,90)")
            .contains("greaterThan(stockQuantity,0)");
    }

    @Test
    void inStockFalseOrNullDoesNotFilterStock() {
        stockCatalog();
        list(null, null, null, null, null, null, null, null, null, false, null, null);
        assertThat(new SpecRecorder().describe(capturedSpec())).doesNotContain("stockQuantity");
    }

    @Test
    void vehicleAndBatteryTypeAreParsedCaseInsensitivelyAndTrimmed() {
        stockCatalog();
        list(null, null, null, null, null, " e_bike ", " Lfp ", null, null, null, null, null);
        String predicate = new SpecRecorder().describe(capturedSpec());
        assertThat(predicate).contains("equal(specification.vehicleType,E_BIKE)").contains("equal(specification.batteryType,LFP)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAR", "E BIKE", "e-bike", "BATTERY;DROP", "1", "xe đạp", "ACCESSORY2", "İ"})
    void invalidVehicleTypeIsBadRequest(String raw) {
        assertThatThrownBy(() -> list(null, null, null, null, null, raw, null, null, null, null, null, null))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).isEqualTo("Bộ lọc loại xe không hợp lệ.");
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"NICKEL", "LI ION", "lithium-ion", "0", "lfp2"})
    void invalidBatteryTypeIsBadRequest(String raw) {
        assertThatThrownBy(() -> list(null, null, null, null, null, null, raw, null, null, null, null, null))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).isEqualTo("Bộ lọc loại pin không hợp lệ.");
            });
    }

    @Test
    void everyDeclaredEnumValueIsAcceptedAsFilter() {
        stockCatalog();
        for (VehicleType type : VehicleType.values()) {
            list(null, null, null, null, null, type.name(), null, null, null, null, null, null);
        }
        for (BatteryType type : BatteryType.values()) {
            list(null, null, null, null, null, null, type.name(), null, null, null, null, null);
        }
    }

    /**
     * Expected: a price range whose minimum is above its maximum can never match, so it is a client mistake (400) or is
     * swapped. Actual: both predicates are simply AND-ed and the shopper gets an empty list without any hint.
     * ProductService.java:69-74.
     */
    @Test
    @Tag("bug")
    void bug_minPriceGreaterThanMaxPriceIsSilentlyAccepted() {
        stockCatalog();
        assertThatThrownBy(() -> list(null, null, "9000000", "1000000", null, null, null, null, null, null, null, null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /** Same mistake for the battery range filter. ProductService.java:84-89. */
    @Test
    @Tag("bug")
    void bug_minRangeGreaterThanMaxRangeIsSilentlyAccepted() {
        stockCatalog();
        assertThatThrownBy(() -> list(null, null, null, null, null, null, null, "120", "40", null, null, null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * Expected: the shopper sees and pays the discount price (the sort and the cards use it), so a price filter must be applied to the
     * effective price. Actual: the filter compares the list price column only, so a 30M bike sold at 18M is hidden from "max 20M".
     * ProductService.java:172-178.
     */
    @Test
    @Tag("bug")
    void bug_priceFilterIgnoresDiscountPrice() {
        stockCatalog();
        list(null, null, null, "20000000", null, null, null, null, null, null, null, null);
        SpecRecorder recorder = new SpecRecorder();
        recorder.describe(capturedSpec());
        String call = recorder.calls.stream().filter(c -> c.startsWith("lessThanOrEqualTo(")).findFirst().orElseThrow();
        assertThat(call).as("max price must also consider discountPrice").doesNotStartWith("lessThanOrEqualTo(price,");
    }

    /**
     * Expected: searching "xe dien" (no diacritics, how most people type on a phone) finds "Xe điện Kinetic". Actual: the predicate is a
     * plain lower(name) LIKE '%xe dien%' with no accent folding, so nothing matches. ProductService.java:180-188.
     */
    @Test
    @Tag("bug")
    void bug_searchWithoutDiacriticsFindsNothing() {
        stockCatalog();
        list("xe dien", null, null, null, null, null, null, null, null, null, null, null);
        SpecRecorder recorder = new SpecRecorder();
        recorder.describe(capturedSpec());
        String pattern = (String) recorder.rawCalls.get("like").get(0)[1];
        String searched = pattern.substring(1, pattern.length() - 1);
        String stored = "Xe điện Kinetic".toLowerCase(Locale.ROOT);
        assertThat(stored.contains(searched)).as("'xe dien' should find 'Xe điện Kinetic'").isTrue();
    }

    /** The reverse direction works today: typing the accents finds the product (guards against a "fix" that breaks it). */
    @Test
    void searchWithDiacriticsMatchesTheStoredName() {
        stockCatalog();
        list("xe điện", null, null, null, null, null, null, null, null, null, null, null);
        SpecRecorder recorder = new SpecRecorder();
        recorder.describe(capturedSpec());
        String pattern = (String) recorder.rawCalls.get("like").get(0)[1];
        assertThat("xe điện kinetic").matches(java.util.regex.Pattern.compile(pattern.replace("%", ".*")));
    }

    /**
     * Expected: lower-casing for a search must not depend on the server's language ("I" must become "i"). Actual: String.toLowerCase()
     * without Locale.ROOT turns "BIKE" into "bıke" on a Turkish server, so nothing matches. ProductService.java:182.
     */
    @Test
    @Tag("bug")
    void bug_queryLowerCasingDependsOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            stockCatalog();
            list("BIKE", null, null, null, null, null, null, null, null, null, null, null);
            SpecRecorder recorder = new SpecRecorder();
            recorder.describe(capturedSpec());
            assertThat((String) recorder.rawCalls.get("like").get(0)[1]).isEqualTo("%bike%");
        } finally {
            Locale.setDefault(previous);
        }
    }

    /**
     * Expected: "%" and "_" typed by a shopper are literal characters (or escaped); a lone "%" must not list the whole catalogue.
     * Actual: the text is put into the LIKE pattern unescaped (no escape character passed). ProductService.java:180-188.
     */
    @Test
    @Tag("bug")
    void bug_likeWildcardsInSearchTextAreNotEscaped() {
        stockCatalog();
        list("50%_off", null, null, null, null, null, null, null, null, null, null, null);
        SpecRecorder recorder = new SpecRecorder();
        recorder.describe(capturedSpec());
        Object[] args = recorder.rawCalls.get("like").get(0);
        boolean escaped = args.length == 3 || ((String) args[1]).contains("\\%") || ((String) args[1]).contains("\\_");
        assertThat(escaped).isTrue();
    }

    // ------------------------------------------------------------------ sorting

    @Test
    void withoutSortTheRepositoryOrderIsKept() {
        stockCatalog(Fixtures.product(1, "B", "200"), Fixtures.product(2, "A", "100"));
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, null, null))).containsExactly("B", "A");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rating", "featured", "popularity", "price;drop", "PRICE ", "id"})
    void unknownSortKeepsRepositoryOrder(String sortBy) {
        // "PRICE " is trimmed + lower-cased, so it is the one key in this list that does sort
        stockCatalog(Fixtures.product(1, "B", "200"), Fixtures.product(2, "A", "100"));
        List<String> result = names(list(null, null, null, null, null, null, null, null, null, null, sortBy, "asc"));
        if (sortBy.trim().equalsIgnoreCase("price")) {
            assertThat(result).containsExactly("A", "B");
        } else {
            assertThat(result).containsExactly("B", "A");
        }
    }

    @Test
    void priceSortUsesTheDiscountPriceWhenThereIsOne() {
        Product discounted = Fixtures.product(1, "Discounted", "30000000");
        discounted.setDiscountPrice(new BigDecimal("15000000"));
        Product mid = Fixtures.product(2, "Mid", "20000000");
        Product cheap = Fixtures.product(3, "Cheap", "10000000");
        stockCatalog(mid, discounted, cheap);
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "price", "asc")))
            .containsExactly("Cheap", "Discounted", "Mid");
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "price", "desc")))
            .containsExactly("Mid", "Discounted", "Cheap");
    }

    @Test
    void sortDirectionDefaultsToAscendingForNullOrGarbageAndIsCaseInsensitiveForDesc() {
        stockCatalog(Fixtures.product(1, "B", "200"), Fixtures.product(2, "A", "100"));
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "price", null))).containsExactly("A", "B");
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "price", "sideways"))).containsExactly("A", "B");
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "price", "DESC"))).containsExactly("B", "A");
    }

    @Test
    void sortIsStableForEqualPrices() {
        stockCatalog(Fixtures.product(1, "First", "100"), Fixtures.product(2, "Second", "100"), Fixtures.product(3, "Third", "100"),
            Fixtures.product(4, "Cheaper", "50"));
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "price", "asc")))
            .containsExactly("Cheaper", "First", "Second", "Third");
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "price", "desc")))
            .containsExactly("First", "Second", "Third", "Cheaper");
    }

    @Test
    void nameSortIsCaseInsensitive() {
        stockCatalog(Fixtures.product(1, "banana", "1"), Fixtures.product(2, "Apple", "1"), Fixtures.product(3, "cherry", "1"));
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "name", "asc")))
            .containsExactly("Apple", "banana", "cherry");
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "NAME", "desc")))
            .containsExactly("cherry", "banana", "Apple");
    }

    /**
     * Expected: a Vietnamese catalogue sorts by Vietnamese alphabet (A, Á, B ... D, Đ, E): "Ánh" comes before "Bình" and "Đại" before "Eco".
     * Actual: names are compared by raw UTF-16 code, so every accented first letter sorts after "z". ProductService.java:260-265.
     */
    @Test
    @Tag("bug")
    void bug_nameSortPutsAccentedVietnameseLettersAfterZ() {
        stockCatalog(Fixtures.product(1, "Bình An", "1"), Fixtures.product(2, "Ánh Dương", "1"), Fixtures.product(3, "Eco", "1"),
            Fixtures.product(4, "Đại Lộ", "1"), Fixtures.product(5, "Zeta", "1"));
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "name", "asc")))
            .containsExactly("Ánh Dương", "Bình An", "Đại Lộ", "Eco", "Zeta");
    }

    @Test
    void newestSortOrdersByCreationTimeAndPutsUnknownDatesLast() {
        Product old = Fixtures.product(1, "Old", "1");
        Fixtures.createdAt(old, LocalDateTime.of(2024, 1, 1, 0, 0));
        Product fresh = Fixtures.product(2, "Fresh", "1");
        Fixtures.createdAt(fresh, LocalDateTime.of(2026, 1, 1, 0, 0));
        Product unknown = Fixtures.product(3, "Unknown", "1");
        stockCatalog(unknown, old, fresh);
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "newest", "desc")))
            .containsExactly("Fresh", "Old", "Unknown");
        assertThat(names(list(null, null, null, null, null, null, null, null, null, null, "newest", "asc")))
            .containsExactly("Old", "Fresh", "Unknown");
    }

    // ------------------------------------------------------------------ summary mapping

    @Test
    void summaryCarriesPriceRatingStockAndCategory() {
        Product product = Fixtures.product(9, "Zoom", "12500000");
        product.setDiscountPrice(new BigDecimal("11000000"));
        product.setRating(new BigDecimal("4.50"));
        product.setReviewCount(12);
        product.setFeatured(true);
        stockCatalog(product);
        ProductSummaryDto dto = list(null, null, null, null, null, null, null, null, null, null, null, null).get(0);
        assertThat(dto.id()).isEqualTo(9L);
        assertThat(dto.price()).isEqualByComparingTo("12500000");
        assertThat(dto.discountPrice()).isEqualByComparingTo("11000000");
        assertThat(dto.rating()).isEqualByComparingTo("4.5");
        assertThat(dto.reviewCount()).isEqualTo(12);
        assertThat(dto.featured()).isTrue();
        assertThat(dto.category().name()).isEqualTo("Xe dien");
    }

    @Test
    void summaryImagesAreActiveOnlyOrderedBySortOrderAndHideInactiveVariantImages() {
        Product product = Fixtures.product(1, "Imgs", "1");
        Fixtures.image(product, 1, "third.png", 3);
        Fixtures.image(product, 2, "first.png", 1);
        ProductImage pending = Fixtures.image(product, 3, "pending.png", 0);
        pending.setStatus(ProductImageStatus.PENDING_DELETE);
        ProductImage deleted = Fixtures.image(product, 4, "deleted.png", 0);
        deleted.setStatus(ProductImageStatus.DELETED);
        ProductImage legacy = Fixtures.image(product, 5, "legacy.png", 2);
        legacy.setStatus(null);
        ProductVariant off = Fixtures.variant(product, 1, "OFF", false);
        off.setActive(false);
        Fixtures.variantImage(product, off, 6, "off-variant.png", 0);
        ProductVariant on = Fixtures.variant(product, 2, "ON", false);
        Fixtures.variantImage(product, on, 7, "on-variant.png", 4);
        stockCatalog(product);
        assertThat(list(null, null, null, null, null, null, null, null, null, null, null, null).get(0).images())
            .containsExactly("first.png", "legacy.png", "third.png", "on-variant.png");
    }

    // ------------------------------------------------------------------ filter options

    @Test
    void filterOptionsAreDistinctSortedTrimmedAndSkipProductsWithoutSpecification() {
        Product a = Fixtures.product(1, "A", "1");
        Fixtures.spec(a, VehicleType.E_SCOOTER, " Yadea ", BatteryType.LFP);
        Product b = Fixtures.product(2, "B", "1");
        Fixtures.spec(b, VehicleType.E_BIKE, "VinFast", BatteryType.LITHIUM_ION);
        Product c = Fixtures.product(3, "C", "1");
        Fixtures.spec(c, VehicleType.E_BIKE, "Yadea", BatteryType.LFP);
        Product noSpec = Fixtures.product(4, "D", "1");
        Product blankBrand = Fixtures.product(5, "E", "1");
        Fixtures.spec(blankBrand, null, "   ");
        stockCatalog(a, b, c, noSpec, blankBrand);

        ProductFilterOptionsDto options = service.getFilterOptions();

        assertThat(options.brands()).containsExactly("VinFast", "Yadea");
        assertThat(options.vehicleTypes()).containsExactly("E_BIKE", "E_SCOOTER");
        assertThat(options.batteryTypes()).containsExactly("LFP", "LITHIUM_ION");
    }

    @Test
    void filterOptionsOnlyConsiderActiveProducts() {
        stockCatalog();
        service.getFilterOptions();
        assertThat(new SpecRecorder().describe(capturedSpec())).isEqualTo("isTrue(active)");
    }

    @Test
    void filterOptionsOfAnEmptyCatalogAreEmptyListsNotNull() {
        stockCatalog();
        ProductFilterOptionsDto options = service.getFilterOptions();
        assertThat(options.brands()).isEmpty();
        assertThat(options.vehicleTypes()).isEmpty();
        assertThat(options.batteryTypes()).isEmpty();
    }

    // ------------------------------------------------------------------ detail by slug

    @Test
    void unknownSlugIsNotFound() {
        when(repository.findDetailBySlug("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProductBySlug("nope"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(e.getReason()).isEqualTo("Không tìm thấy sản phẩm.");
            });
    }

    @Test
    void nullSlugIsNotFoundNotAServerError() {
        assertThatThrownBy(() -> service.getProductBySlug(null))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void inactiveProductIsNotFoundBySlug() {
        Product product = Fixtures.product(1, "Hidden", "1");
        product.setActive(false);
        when(repository.findDetailBySlug("hidden")).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.getProductBySlug("hidden"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void productWithNullActiveFlagIsTreatedAsHidden() {
        Product product = Fixtures.product(1, "Odd", "1");
        product.setActive(null);
        when(repository.findDetailBySlug("odd")).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.getProductBySlug("odd")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void detailExposesSpecificationAndRegistrationFlag() {
        Product product = Fixtures.product(1, "Fast", "1");
        ProductSpecification spec = Fixtures.spec(product, VehicleType.E_BIKE, "Brand", BatteryType.LFP);
        spec.setMaxSpeedKmh(new BigDecimal("45"));
        spec.setMotorPowerWatts(1500);
        spec.setWarrantyMonths(24);
        when(repository.findDetailBySlug("fast")).thenReturn(Optional.of(product));
        ProductDetailDto dto = service.getProductBySlug("fast");
        assertThat(dto.specification().vehicleType()).isEqualTo("E_BIKE");
        assertThat(dto.specification().batteryType()).isEqualTo("LFP");
        assertThat(dto.specification().warrantyMonths()).isEqualTo(24);
        assertThat(dto.specification().requiresRegistration()).isTrue();
    }

    @Test
    void detailWithoutSpecificationHasNullSpecification() {
        Product product = Fixtures.product(1, "Plain", "1");
        when(repository.findDetailBySlug("plain")).thenReturn(Optional.of(product));
        assertThat(service.getProductBySlug("plain").specification()).isNull();
    }

    @Test
    void detailVariantsOnlyActiveWithAnImageDefaultFirstThenById() {
        Product product = Fixtures.product(1, "Colours", "1");
        ProductVariant red = Fixtures.variant(product, 5, "RED", false);
        Fixtures.variantImage(product, red, 10, "red.png", 0);
        ProductVariant blue = Fixtures.variant(product, 3, "BLUE", true);
        Fixtures.variantImage(product, blue, 11, "blue-2.png", 2);
        Fixtures.variantImage(product, blue, 12, "blue-1.png", 1);
        ProductImage removed = Fixtures.variantImage(product, blue, 13, "blue-removed.png", 0);
        removed.setStatus(ProductImageStatus.DELETED);
        ProductVariant noImage = Fixtures.variant(product, 4, "NOIMG", false);
        ProductVariant off = Fixtures.variant(product, 1, "OFF", false);
        off.setActive(false);
        Fixtures.variantImage(product, off, 14, "off.png", 0);
        ProductVariant green = Fixtures.variant(product, 2, "GREEN", false);
        Fixtures.variantImage(product, green, 15, "green.png", 0);
        when(repository.findDetailBySlug("colours")).thenReturn(Optional.of(product));

        ProductDetailDto dto = service.getProductBySlug("colours");

        assertThat(dto.variants()).extracting(v -> v.sku()).containsExactly("BLUE", "GREEN", "RED");
        assertThat(dto.variants().get(0).images()).containsExactly("blue-1.png", "blue-2.png");
        assertThat(dto.variants().get(0).imageUrl()).isEqualTo("blue-1.png");
        assertThat(noImage.getActive()).isTrue();
    }

    // ------------------------------------------------------------------ related products

    private Product related(long id, String name, int categoryId, String brand, VehicleType type, String price) {
        Product product = Fixtures.product(id, name, price);
        product.setCategory(Fixtures.category(categoryId, "Cat " + categoryId));
        Fixtures.spec(product, type, brand);
        return product;
    }

    private void relatedWorld(Product base, Product... others) {
        when(repository.findBySlug(base.getSlug())).thenReturn(Optional.of(base));
        List<Product> all = new ArrayList<>(List.of(base));
        all.addAll(List.of(others));
        when(repository.findAll(ArgumentMatchers.<Specification<Product>>any())).thenReturn(all);
    }

    @Test
    void relatedProductsOfAnUnknownOrInactiveSlugAreNotFound() {
        when(repository.findBySlug("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRelatedProducts("ghost", 4))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        Product off = Fixtures.product(1, "Off", "1");
        off.setActive(false);
        when(repository.findBySlug("off")).thenReturn(Optional.of(off));
        assertThatThrownBy(() -> service.getRelatedProducts("off", 4)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void relatedProductsExcludeTheProductItselfAndUnrelatedOnes() {
        Product base = related(1, "Base", 1, "Yadea", VehicleType.E_BIKE, "10000000");
        Product sameCategory = related(2, "SameCat", 1, "Other", VehicleType.E_SCOOTER, "99000000");
        Product nothingInCommon = related(3, "Nothing", 2, "Other", VehicleType.E_SCOOTER, "99000000");
        relatedWorld(base, sameCategory, nothingInCommon);
        assertThat(names(service.getRelatedProducts("base", 4))).containsExactly("SameCat");
    }

    @Test
    void relatedProductsAreRankedByCategoryBrandVehicleAndPrice() {
        Product base = related(1, "Base", 1, "Yadea", VehicleType.E_BIKE, "10000000");
        Product everything = related(2, "Everything", 1, "yadea ", VehicleType.E_BIKE, "11000000"); // 5+3+3+2
        Product categoryOnly = related(3, "CategoryOnly", 1, "Zed", VehicleType.E_SCOOTER, "50000000"); // 5
        Product brandVehiclePrice = related(4, "BrandVehiclePrice", 2, "YADEA", VehicleType.E_BIKE, "9000000"); // 3+3+2
        Product priceOnly = related(5, "PriceOnly", 2, "Zed", VehicleType.E_SCOOTER, "12000000"); // 2
        relatedWorld(base, priceOnly, categoryOnly, brandVehiclePrice, everything);
        assertThat(names(service.getRelatedProducts("base", 10)))
            .containsExactly("Everything", "BrandVehiclePrice", "CategoryOnly", "PriceOnly");
    }

    @Test
    void priceNearnessBoundariesAreInclusiveAtSeventyAndOneHundredThirtyPercent() {
        Product base = related(1, "Base", 1, "A", VehicleType.E_BIKE, "100");
        Product lower = related(2, "Lower", 9, "B", VehicleType.E_SCOOTER, "70");
        Product upper = related(3, "Upper", 9, "B", VehicleType.E_SCOOTER, "130");
        Product justBelow = related(4, "JustBelow", 9, "B", VehicleType.E_SCOOTER, "69.99");
        Product justAbove = related(5, "JustAbove", 9, "B", VehicleType.E_SCOOTER, "130.01");
        relatedWorld(base, lower, upper, justBelow, justAbove);
        assertThat(names(service.getRelatedProducts("base", 10))).containsExactlyInAnyOrder("Lower", "Upper");
    }

    @Test
    void relatedPriceNearnessUsesTheDiscountPrice() {
        Product base = related(1, "Base", 1, "A", VehicleType.E_BIKE, "100");
        base.setDiscountPrice(new BigDecimal("50"));
        Product near = related(2, "Near", 9, "B", VehicleType.E_SCOOTER, "200");
        near.setDiscountPrice(new BigDecimal("55"));
        Product far = related(3, "Far", 9, "B", VehicleType.E_SCOOTER, "100");
        relatedWorld(base, near, far);
        assertThat(names(service.getRelatedProducts("base", 10))).containsExactly("Near");
    }

    @Test
    void zeroPricedBaseProductNeverCountsAnyoneAsNearInPrice() {
        Product base = related(1, "Base", 1, "A", VehicleType.E_BIKE, "0");
        Product free = related(2, "Free", 9, "B", VehicleType.E_SCOOTER, "0");
        relatedWorld(base, free);
        assertThat(service.getRelatedProducts("base", 4)).isEmpty();
    }

    @Test
    void relatedTiesAreBrokenByRatingDescendingThenName() {
        Product base = related(1, "Base", 1, "A", VehicleType.E_BIKE, "100");
        Product lowRated = related(2, "Zulu", 1, "B", VehicleType.E_SCOOTER, "1000");
        lowRated.setRating(new BigDecimal("3.0"));
        Product highRated = related(3, "Yankee", 1, "B", VehicleType.E_SCOOTER, "1000");
        highRated.setRating(new BigDecimal("4.9"));
        Product unrated = related(4, "Alpha", 1, "B", VehicleType.E_SCOOTER, "1000");
        unrated.setRating(null);
        Product sameRatingA = related(5, "Bravo", 1, "B", VehicleType.E_SCOOTER, "1000");
        sameRatingA.setRating(new BigDecimal("3.0"));
        relatedWorld(base, lowRated, unrated, highRated, sameRatingA);
        assertThat(names(service.getRelatedProducts("base", 10))).containsExactly("Yankee", "Bravo", "Zulu", "Alpha");
    }

    @Test
    void relatedLimitDefaultsToFourClampsToTwelveAndRejectsNothing() {
        Product base = related(1, "Base", 1, "A", VehicleType.E_BIKE, "100");
        List<Product> others = new ArrayList<>();
        for (int i = 2; i <= 20; i++) {
            others.add(related(i, "P" + String.format("%02d", i), 1, "B", VehicleType.E_SCOOTER, "1000"));
        }
        relatedWorld(base, others.toArray(new Product[0]));
        assertThat(service.getRelatedProducts("base", null)).hasSize(4);
        assertThat(service.getRelatedProducts("base", 0)).hasSize(4);
        assertThat(service.getRelatedProducts("base", -5)).hasSize(4);
        assertThat(service.getRelatedProducts("base", 1)).hasSize(1);
        assertThat(service.getRelatedProducts("base", 12)).hasSize(12);
        assertThat(service.getRelatedProducts("base", 13)).hasSize(12);
        assertThat(service.getRelatedProducts("base", Integer.MAX_VALUE)).hasSize(12);
    }

    @Test
    void relatedWithProductsLackingSpecificationOrCategoryDoesNotCrash() {
        Product base = Fixtures.product(1, "Base", "100");
        base.setCategory(null);
        Product other = Fixtures.product(2, "Other", "100");
        other.setCategory(null);
        relatedWorld(base, other);
        // only the price is comparable: still related, and toSummaryDto needs a category so give the candidate one
        other.setCategory(Fixtures.category(3, "X"));
        assertThat(names(service.getRelatedProducts("base", 4))).containsExactly("Other");
    }

    // ------------------------------------------------------------------ caching contract

    @Test
    void listAndDetailAreCachedInTheirOwnNamedCaches() throws Exception {
        Method list = ProductService.class.getMethod("getProducts", String.class, Integer.class, BigDecimal.class, BigDecimal.class,
            String.class, String.class, String.class, BigDecimal.class, BigDecimal.class, Boolean.class, String.class, String.class);
        Cacheable cacheList = list.getAnnotation(Cacheable.class);
        assertThat(cacheList).isNotNull();
        assertThat(cacheList.cacheNames()).containsExactly(CacheNames.PRODUCT_LIST_CACHE);

        Cacheable cacheDetail = ProductService.class.getMethod("getProductBySlug", String.class).getAnnotation(Cacheable.class);
        assertThat(cacheDetail.cacheNames()).containsExactly(CacheNames.PRODUCT_DETAIL_CACHE);
        assertThat(cacheDetail.key()).isEqualTo("#slug");
    }

    @Test
    void relatedProductsAndFilterOptionsAreNotCachedSoTheyNeverGoStale() throws Exception {
        assertThat(ProductService.class.getMethod("getRelatedProducts", String.class, Integer.class).getAnnotation(Cacheable.class)).isNull();
        assertThat(ProductService.class.getMethod("getFilterOptions").getAnnotation(Cacheable.class)).isNull();
    }

    @Test
    void anyUnusedMockedRepositoryMethodIsNeverCalledForFilterOptions() {
        stockCatalog();
        service.getFilterOptions();
        verify(repository).findAll(any(Specification.class));
    }
}
