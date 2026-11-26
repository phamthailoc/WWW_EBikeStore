package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebike.dto.admin.request.CatalogCategoryRequest;
import com.ebike.dto.admin.request.CatalogProductRequest;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.service.admin.CatalogRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CatalogRulesTest {

    private static void is400(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
            .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    private static CatalogProductRequest product(String name, Integer category, String price, String discount) {
        return new CatalogProductRequest(name, null, null, category, price == null ? null : new BigDecimal(price),
            discount == null ? null : new BigDecimal(discount), null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static CatalogProductRequest withSpecs(BigDecimal ah, BigDecimal v, BigDecimal speed, BigDecimal range, Integer watts, Integer warranty) {
        return new CatalogProductRequest("Amy", null, null, 1, new BigDecimal("1000"), null, null, null, null, null, null, null,
            ah, v, speed, range, watts, warranty);
    }

    // ------------------------------------------------------------------ slugs

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Xe điện Dibao|xe-dien-dibao", "  Đạp   Xe!! |dap-xe", "ĐẠP ĐIỆN|dap-dien", "A--B|a-b", "---x---|x",
        "VinFast Klara S 2022|vinfast-klara-s-2022", "Ơ Ư Ê|o-u-e"})
    void slugify_removesDiacritics_collapsesSeparators(String text, String slug) {
        assertThat(CatalogRules.slugify(text)).isEqualTo(slug);
    }

    @Test
    void slugify_nullOrSymbolsOnly_isEmpty() {
        assertThat(CatalogRules.slugify(null)).isEmpty();
        assertThat(CatalogRules.slugify("!!! ???")).isEmpty();
        assertThat(CatalogRules.slugify("日本語")).isEmpty();
    }

    @Test
    void slugFor_prefersRequested_otherwiseDerivesFromName() {
        assertThat(CatalogRules.slugFor("  my-slug ", "Ignored", 255)).isEqualTo("my-slug");
        assertThat(CatalogRules.slugFor(null, "Xe Điện", 255)).isEqualTo("xe-dien");
        assertThat(CatalogRules.slugFor("   ", "Xe Điện", 255)).isEqualTo("xe-dien");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Has-Upper", "has space", "-lead", "trail-", "double--dash", "under_score", "dấu", "a/b", "../x"})
    void slugFor_rejectsMalformedRequestedSlug(String bad) {
        is400(() -> CatalogRules.slugFor(bad, "Name", 255));
    }

    @Test
    void slugFor_lengthBoundary_andUnslugifiableName() {
        assertThat(CatalogRules.slugFor("a".repeat(100), "n", 100)).hasSize(100);
        is400(() -> CatalogRules.slugFor("a".repeat(101), "n", 100));
        is400(() -> CatalogRules.slugFor(null, "!!!", 100));
        is400(() -> CatalogRules.slugFor(null, null, 100));
        is400(() -> CatalogRules.slugFor(null, "b".repeat(101), 100));
    }

    // ------------------------------------------------------------------ names and texts

    @Test
    void requiredName_trims_minTwo_maxLimit() {
        assertThat(CatalogRules.requiredName("  ab ", 5, "Tên")).isEqualTo("ab");
        is400(() -> CatalogRules.requiredName("a", 5, "Tên"));
        is400(() -> CatalogRules.requiredName("  a  ", 5, "Tên"));
        is400(() -> CatalogRules.requiredName(null, 5, "Tên"));
        is400(() -> CatalogRules.requiredName("   ", 5, "Tên"));
        assertThat(CatalogRules.requiredName("abcde", 5, "Tên")).isEqualTo("abcde");
        is400(() -> CatalogRules.requiredName("abcdef", 5, "Tên"));
    }

    @Test
    void optionalText_blankIsNull_trimmed_maxEnforcedAfterTrim() {
        assertThat(CatalogRules.optionalText(null, 3, "x")).isNull();
        assertThat(CatalogRules.optionalText("   ", 3, "x")).isNull();
        assertThat(CatalogRules.optionalText("  abc ", 3, "x")).isEqualTo("abc");
        is400(() -> CatalogRules.optionalText("abcd", 3, "x"));
    }

    @Test
    void enumValue_blankIsNull_caseInsensitive_garbageRejected() {
        assertThat(CatalogRules.enumValue(VehicleType.class, null, "x")).isNull();
        assertThat(CatalogRules.enumValue(VehicleType.class, "  ", "x")).isNull();
        assertThat(CatalogRules.enumValue(VehicleType.class, " e_bike ", "x")).isEqualTo(VehicleType.E_BIKE);
        is400(() -> CatalogRules.enumValue(VehicleType.class, "HOVERBOARD", "x"));
    }

    // ------------------------------------------------------------------ prices

    @ParameterizedTest
    @CsvSource({"1,true", "99999999,true", "99999999.00,true", "1000.00,true", "100000000,false", "0,false", "-5,false", "0.5,false", "1000.5,false"})
    void checkPrices_priceBoundaries(String price, boolean ok) {
        if (ok) {
            assertThatCode(() -> CatalogRules.checkPrices(new BigDecimal(price), null)).doesNotThrowAnyException();
        } else {
            is400(() -> CatalogRules.checkPrices(new BigDecimal(price), null));
        }
    }

    @Test
    void checkPrices_nullPrice_refused() {
        is400(() -> CatalogRules.checkPrices(null, null));
    }

    @ParameterizedTest
    @CsvSource({"999,true", "1,true", "1000,false", "1001,false", "0,false", "-1,false", "500.5,false"})
    void checkPrices_discountMustBeWholePositiveAndBelowPrice(String discount, boolean ok) {
        if (ok) {
            assertThatCode(() -> CatalogRules.checkPrices(new BigDecimal("1000"), new BigDecimal(discount))).doesNotThrowAnyException();
        } else {
            is400(() -> CatalogRules.checkPrices(new BigDecimal("1000"), new BigDecimal(discount)));
        }
    }

    // ------------------------------------------------------------------ product / category

    @Test
    void checkProduct_nullRequestNameCategory() {
        is400(() -> CatalogRules.checkProduct(null));
        is400(() -> CatalogRules.checkProduct(product(null, 1, "1000", null)));
        is400(() -> CatalogRules.checkProduct(product("x", 1, "1000", null)));
        is400(() -> CatalogRules.checkProduct(product("A".repeat(256), 1, "1000", null)));
        is400(() -> CatalogRules.checkProduct(product("Amy", null, "1000", null)));
        assertThatCode(() -> CatalogRules.checkProduct(product("A".repeat(255), 1, "1000", null))).doesNotThrowAnyException();
    }

    @Test
    void checkProduct_descriptionLimit() {
        CatalogProductRequest big = new CatalogProductRequest("Amy", null, "d".repeat(5001), 1, new BigDecimal("1000"), null, null, null,
            null, null, null, null, null, null, null, null, null, null);
        is400(() -> CatalogRules.checkProduct(big));
        CatalogProductRequest ok = new CatalogProductRequest("Amy", null, "d".repeat(5000), 1, new BigDecimal("1000"), null, null, null,
            null, null, null, null, null, null, null, null, null, null);
        assertThatCode(() -> CatalogRules.checkProduct(ok)).doesNotThrowAnyException();
    }

    @Test
    void checkProduct_enumFields() {
        CatalogProductRequest badType = new CatalogProductRequest("Amy", null, null, 1, new BigDecimal("1000"), null, null, null, null, null,
            "CAR", null, null, null, null, null, null, null);
        is400(() -> CatalogRules.checkProduct(badType));
        CatalogProductRequest badBattery = new CatalogProductRequest("Amy", null, null, 1, new BigDecimal("1000"), null, null, null, null, null,
            "E_BIKE", "NUCLEAR", null, null, null, null, null, null);
        is400(() -> CatalogRules.checkProduct(badBattery));
    }

    @Test
    void checkProduct_numericSpecs_boundaries() {
        BigDecimal one = BigDecimal.ONE;
        assertThatCode(() -> CatalogRules.checkProduct(withSpecs(new BigDecimal("100000"), new BigDecimal("10000"), new BigDecimal("1000"),
            new BigDecimal("9999.99"), 100000, 120))).doesNotThrowAnyException();
        is400(() -> CatalogRules.checkProduct(withSpecs(new BigDecimal("100000.01"), one, one, one, 1, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, new BigDecimal("10001"), one, one, 1, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, one, new BigDecimal("1000.01"), one, 1, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, one, one, new BigDecimal("10000"), 1, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(BigDecimal.ZERO, one, one, one, 1, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, new BigDecimal("-1"), one, one, 1, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, one, one, one, 0, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, one, one, one, 100001, 1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, one, one, one, 1, -1)));
        is400(() -> CatalogRules.checkProduct(withSpecs(one, one, one, one, 1, 121)));
        assertThatCode(() -> CatalogRules.checkProduct(withSpecs(null, null, null, null, null, 0))).doesNotThrowAnyException();
    }

    @Test
    void checkProduct_brandAndModelLength() {
        CatalogProductRequest longBrand = new CatalogProductRequest("Amy", null, null, 1, new BigDecimal("1000"), null, null, null,
            "b".repeat(101), null, null, null, null, null, null, null, null, null);
        is400(() -> CatalogRules.checkProduct(longBrand));
        CatalogProductRequest longModel = new CatalogProductRequest("Amy", null, null, 1, new BigDecimal("1000"), null, null, null,
            null, "m".repeat(101), null, null, null, null, null, null, null, null);
        is400(() -> CatalogRules.checkProduct(longModel));
    }

    @Test
    void checkCategory_rules() {
        is400(() -> CatalogRules.checkCategory(null));
        is400(() -> CatalogRules.checkCategory(new CatalogCategoryRequest("x", null, null, null)));
        is400(() -> CatalogRules.checkCategory(new CatalogCategoryRequest("c".repeat(101), null, null, null)));
        is400(() -> CatalogRules.checkCategory(new CatalogCategoryRequest("ok", null, "d".repeat(5001), null)));
        assertThatCode(() -> CatalogRules.checkCategory(new CatalogCategoryRequest("c".repeat(100), null, null, null))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void checkCategory_blankName_refused(String name) {
        is400(() -> CatalogRules.checkCategory(new CatalogCategoryRequest(name, null, null, null)));
    }
}
