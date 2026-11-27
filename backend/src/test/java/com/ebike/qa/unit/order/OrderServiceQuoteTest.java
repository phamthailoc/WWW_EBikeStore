package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.dto.order.response.OrderQuoteResponse;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceQuoteTest extends OrderServiceTestBase {

    Product bike;

    @BeforeEach
    void data() {
        bike = OrderFixtures.bike(1L, "Kinetic X1", "10000000", 5, VehicleType.E_BIKE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(bike));
    }

    private static OrderQuoteRequest quote(List<OrderCreateItemRequest> items) {
        return new OrderQuoteRequest(false, items, null);
    }

    private static List<OrderCreateItemRequest> one(long id, int qty) {
        return List.of(new OrderCreateItemRequest(id, qty, null));
    }

    private void assertBadRequest(OrderQuoteRequest request) {
        assertThatThrownBy(() -> service.quoteOrder(request)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private Promotion promo(String type, String value) {
        Promotion promotion = new Promotion();
        ReflectionTestUtils.setField(promotion, "id", 5L);
        promotion.setCode("CODE");
        promotion.setDiscountType(type);
        promotion.setDiscountValue(new BigDecimal(value));
        promotion.setStatus("LIVE");
        when(promotionRepository.findByCodeIgnoreCase("CODE")).thenReturn(Optional.of(promotion));
        return promotion;
    }

    @Test
    void nullRequestAndEmptyItemsAre400() {
        assertBadRequest(null);
        assertBadRequest(quote(null));
        assertBadRequest(quote(List.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11})
    void quantityOutsideOneToTenIs400(int qty) {
        assertBadRequest(quote(one(1L, qty)));
    }

    @Test
    void moreThanTwentyLinesIs400() {
        List<OrderCreateItemRequest> lines = java.util.stream.IntStream.range(0, 21).mapToObj(i -> new OrderCreateItemRequest(1L, 1, null)).toList();
        assertBadRequest(quote(lines));
    }

    @Test
    void simplePickupQuote() {
        OrderQuoteResponse quote = service.quoteOrder(quote(one(1L, 2)));
        assertThat(quote.subtotal()).isEqualByComparingTo("20000000");
        assertThat(quote.shippingFee()).isEqualByComparingTo("0");
        assertThat(quote.discountAmount()).isEqualByComparingTo("0");
        assertThat(quote.registrationFee()).isEqualByComparingTo("0");
        assertThat(quote.totalAmount()).isEqualByComparingTo("20000000");
        assertThat(quote.registrationRequired()).isFalse();
        assertThat(quote.registrationServicePerBike()).isEqualByComparingTo("500000");
    }

    @Test
    void quoteDoesNotTouchStockOrUsageCounters() {
        promo("PERCENTAGE", "10");
        service.quoteOrder(new OrderQuoteRequest(false, one(1L, 2), "CODE"));
        assertThat(bike.getStockQuantity()).isEqualTo(5);
        verify(promotionRepository, never()).tryIncrementUsage(5L);
        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void quoteUsesDiscountPriceAndColourSurcharge() {
        bike.setDiscountPrice(new BigDecimal("9000000"));
        OrderFixtures.colour(11L, bike, "Red", 5, "250000");
        OrderQuoteResponse quote = service.quoteOrder(quote(List.of(new OrderCreateItemRequest(1L, 2, 11L))));
        assertThat(quote.subtotal()).isEqualByComparingTo("18500000");
    }

    @Test
    void quoteRefusesMoreThanStockAndTreatsNullStockAsSoldOut() {
        assertBadRequest(quote(one(1L, 6)));
        bike.setStockQuantity(null);
        assertBadRequest(quote(one(1L, 1)));
    }

    @Test
    void exactlyTheStockIsQuotable() {
        assertThat(service.quoteOrder(quote(one(1L, 5))).subtotal()).isEqualByComparingTo("50000000");
    }

    @Test
    void sameBikeOnTwoLinesIsCheckedAgainstTheTotalStock() {
        assertBadRequest(quote(List.of(new OrderCreateItemRequest(1L, 3, null), new OrderCreateItemRequest(1L, 3, null))));
    }

    @Test
    void sameColourOnTwoLinesIsCheckedAgainstTheColourStock() {
        OrderFixtures.colour(11L, bike, "Red", 4, null);
        assertBadRequest(quote(List.of(new OrderCreateItemRequest(1L, 3, 11L), new OrderCreateItemRequest(1L, 3, 11L))));
    }

    @Test
    void unknownProductIs404() {
        assertThatThrownBy(() -> service.quoteOrder(quote(one(77L, 1)))).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void percentageDiscountIsRoundedHalfUpAndCapped() {
        bike.setPrice(new BigDecimal("110"));
        promo("PERCENTAGE", "5");
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 1), "CODE")).discountAmount()).isEqualByComparingTo("6");
        bike.setPrice(new BigDecimal("10000000"));
        promo("PERCENTAGE", "50").setMaxDiscountAmount(new BigDecimal("1000000"));
        OrderQuoteResponse quote = service.quoteOrder(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
        assertThat(quote.discountAmount()).isEqualByComparingTo("1000000");
        assertThat(quote.totalAmount()).isEqualByComparingTo("9000000");
    }

    /** Defence in depth (U-06): a corrupt promotion row (negative cap or negative value) can never turn the discount into a surcharge. */
    @Test
    void aCorruptPromotionNeverGivesANegativeDiscountOrMoreThanTheSubtotal() {
        promo("PERCENTAGE", "10").setMaxDiscountAmount(new BigDecimal("-50000"));
        OrderQuoteResponse capped = service.quoteOrder(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
        assertThat(capped.discountAmount()).isEqualByComparingTo("0");
        assertThat(capped.totalAmount()).isLessThanOrEqualTo(capped.subtotal());
        promo("FIXED_AMOUNT", "-5000");
        OrderQuoteResponse fixed = service.quoteOrder(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
        assertThat(fixed.discountAmount()).isEqualByComparingTo("0");
        promo("FIXED_AMOUNT", "99000000");
        OrderQuoteResponse huge = service.quoteOrder(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
        assertThat(huge.discountAmount()).isEqualByComparingTo(huge.subtotal());
    }

    @Test
    void fixedDiscountIsLimitedToTheSubtotal() {
        promo("FIXED_AMOUNT", "99000000");
        OrderQuoteResponse quote = service.quoteOrder(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
        assertThat(quote.discountAmount()).isEqualByComparingTo("10000000");
        assertThat(quote.totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void anUnusableCodeIs400() {
        assertBadRequest(new OrderQuoteRequest(false, one(1L, 1), "NOPE"));
        Promotion promotion = promo("PERCENTAGE", "10");
        promotion.setStatus("WAITING");
        assertBadRequest(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
        promotion.setStatus("LIVE");
        promotion.setEndsAt(LocalDateTime.now().minusMinutes(1));
        assertBadRequest(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
        promotion.setEndsAt(null);
        promotion.setUsageLimit(3);
        promotion.setUsageCount(3);
        assertBadRequest(new OrderQuoteRequest(false, one(1L, 1), "CODE"));
    }

    @Test
    void blankCodeMeansNoDiscount() {
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 1), "   ")).discountAmount()).isEqualByComparingTo("0");
    }

    @Test
    void registrationTaxReferenceIsTwoAndFivePercentOfTheSubtotalAndNeverInTheTotal() {
        OrderQuoteResponse quote = service.quoteOrder(quote(one(1L, 2)));
        assertThat(quote.registrationTaxLow()).isEqualByComparingTo("400000");
        assertThat(quote.registrationTaxHigh()).isEqualByComparingTo("1000000");
        assertThat(quote.totalAmount()).isEqualByComparingTo("20000000");
    }

    @Test
    void registrationTaxIsRoundedHalfUp() {
        bike.setPrice(new BigDecimal("125")); // 2% = 2.5 -> 3 ; 5% = 6.25 -> 6
        OrderQuoteResponse quote = service.quoteOrder(quote(one(1L, 1)));
        assertThat(quote.registrationTaxLow()).isEqualByComparingTo("3");
        assertThat(quote.registrationTaxHigh()).isEqualByComparingTo("6");
    }

    @Test
    void registrationFeeCountsOnlyBikesThatNeedAPlate() {
        Product moto = OrderFixtures.motorbike(2L, "30000000", 5);
        Product helmet = OrderFixtures.accessory(3L, "500000", 9);
        when(productRepository.findById(2L)).thenReturn(Optional.of(moto));
        when(productRepository.findById(3L)).thenReturn(Optional.of(helmet));
        OrderQuoteResponse quote = service.quoteOrder(new OrderQuoteRequest(true,
            List.of(new OrderCreateItemRequest(2L, 2, null), new OrderCreateItemRequest(3L, 5, null)), null));
        assertThat(quote.registrationFee()).isEqualByComparingTo("1000000");
        assertThat(quote.registrationRequired()).isTrue();
        assertThat(quote.registrationServiceAvailable()).isTrue();
        assertThat(quote.totalAmount()).isEqualByComparingTo("63500000");
    }

    @Test
    void aFastEBikeNeedsAPlateEvenThoughItIsNotAMotorbike() {
        bike.getSpecification().setMaxSpeedKmh(new BigDecimal("26"));
        assertThat(service.quoteOrder(quote(one(1L, 1))).registrationRequired()).isTrue();
        bike.getSpecification().setMaxSpeedKmh(new BigDecimal("25"));
        assertThat(service.quoteOrder(quote(one(1L, 1))).registrationRequired()).isFalse();
        bike.getSpecification().setMotorPowerWatts(251);
        assertThat(service.quoteOrder(quote(one(1L, 1))).registrationRequired()).isTrue();
        bike.getSpecification().setMotorPowerWatts(250);
        assertThat(service.quoteOrder(quote(one(1L, 1))).registrationRequired()).isFalse();
    }

    @Test
    void aTickedRegistrationIsIgnoredInAQuoteWhenTheShopSwitchedItOffOrItIsHomeDelivery() {
        Product moto = OrderFixtures.motorbike(2L, "30000000", 5);
        when(productRepository.findById(2L)).thenReturn(Optional.of(moto));
        when(pricingService.registrationServiceOffered()).thenReturn(false);
        OrderQuoteResponse off = service.quoteOrder(new OrderQuoteRequest(true, one(2L, 1), null));
        assertThat(off.registrationFee()).isEqualByComparingTo("0");
        assertThat(off.registrationServiceAvailable()).isFalse();

        when(pricingService.registrationServiceOffered()).thenReturn(true);
        OrderQuoteResponse home = service.quoteOrder(new OrderQuoteRequest(true, one(2L, 1), null, "HOME_DELIVERY", "Hà Nội"));
        assertThat(home.registrationFee()).isEqualByComparingTo("0");
        assertThat(home.registrationServiceAvailable()).isFalse();
    }

    @Test
    void homeDeliveryFeeIsPerBikeAndDependsOnTheProvince() {
        when(showroomRepository.findByActiveTrueOrderByDistrictAscNameAsc())
            .thenReturn(List.of(OrderFixtures.showroom(1L, "Hà Nội", "Cầu Giấy", true)));
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 3), null, "HOME_DELIVERY", "ha noi")).shippingFee()).isEqualByComparingTo("600000");
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 3), null, "HOME_DELIVERY", "Cần Thơ")).shippingFee()).isEqualByComparingTo("2400000");
    }

    @Test
    void homeDeliveryWithoutAProvinceChargesNothingYet() {
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 3), null, "HOME_DELIVERY", null)).shippingFee()).isEqualByComparingTo("0");
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 3), null, "HOME_DELIVERY", "  ")).shippingFee()).isEqualByComparingTo("0");
    }

    @Test
    void storePickupAndMissingMethodAreFree() {
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 3), null, "STORE_PICKUP", "Hà Nội")).shippingFee()).isEqualByComparingTo("0");
        assertThat(service.quoteOrder(new OrderQuoteRequest(false, one(1L, 3), null, null, "Hà Nội")).shippingFee()).isEqualByComparingTo("0");
    }

    @Test
    void unknownDeliveryMethodIs400() {
        assertBadRequest(new OrderQuoteRequest(false, one(1L, 1), null, "TELEPORT", null));
    }

    @Test
    void totalIsSubtotalPlusFeesMinusDiscount() {
        Product moto = OrderFixtures.motorbike(2L, "30000000", 5);
        when(productRepository.findById(2L)).thenReturn(Optional.of(moto));
        promo("PERCENTAGE", "10");
        OrderQuoteResponse quote = service.quoteOrder(new OrderQuoteRequest(true, one(2L, 2), "CODE"));
        // 60,000,000 - 6,000,000 + 2 x 500,000 registration
        assertThat(quote.totalAmount()).isEqualByComparingTo("55000000");
        ProductVariant unused = null;
        assertThat(unused).isNull();
    }
}
