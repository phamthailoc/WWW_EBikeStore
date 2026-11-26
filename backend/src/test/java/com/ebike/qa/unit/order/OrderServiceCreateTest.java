package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.event.notification.OrderPlacedEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.installment.InstallmentRules;
import com.ebike.service.stock.StockLedger;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceCreateTest extends OrderServiceTestBase {

    Product bike;
    User alice;
    Authentication aliceAuth;

    @BeforeEach
    void data() {
        bike = OrderFixtures.bike(1L, "Kinetic X1", "10000000", 5, VehicleType.E_BIKE);
        alice = OrderFixtures.user(1L, "alice", "alice@example.com");
        aliceAuth = OrderFixtures.customer("alice");
        when(productRepository.findById(1L)).thenReturn(Optional.of(bike));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(showroomRepository.findById(1L)).thenReturn(Optional.of(OrderFixtures.showroom(1L, "Hà Nội", "Cầu Giấy", true)));
    }

    @AfterEach
    void resetBridge() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    private void assertRejected(OrderCreateRequest request) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(request, aliceAuth));
        // nothing is saved; a stock figure changed in memory before the failure is undone by the transaction rollback, so it is not asserted here
        verify(orderRepository, never()).save(any());
    }

    private Order saved() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private Promotion promotion(long id, String code, String type, String value) {
        Promotion promotion = new Promotion();
        ReflectionTestUtils.setField(promotion, "id", id);
        promotion.setCode(code);
        promotion.setDiscountType(type);
        promotion.setDiscountValue(new BigDecimal(value));
        promotion.setStatus("LIVE");
        promotion.setUsageLimit(0);
        promotion.setUsageCount(0);
        when(promotionRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(promotion));
        return promotion;
    }

    // --- happy path ----------------------------------------------------------------------------------

    @Test
    void storePickupOrderIsPriceStockAndPaymentConsistent() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        new NotificationEventBridge().setApplicationEventPublisher(publisher);

        OrderResponse response = service.createOrder(pickupRequest(1L, 2), aliceAuth);

        Order order = saved();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderNumber()).matches("ORD-\\d{8}-[0-9A-F]{8}");
        assertThat(order.getUser()).isSameAs(alice);
        assertThat(order.getSubtotal()).isEqualByComparingTo("20000000");
        assertThat(order.getShippingFee()).isEqualByComparingTo("0");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(order.getRegistrationFee()).isEqualByComparingTo("0");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20000000");
        assertThat(order.getCustomerEmail()).isEqualTo("khach@example.com");
        assertThat(order.getPayment().getPaymentMethod()).isEqualTo(PaymentMethod.PAY_LATER);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getPayment().getAmount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(order.getShipment().getPickupShowroom()).isNotNull();
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().iterator().next().getLineTotal()).isEqualByComparingTo("20000000");
        assertThat(bike.getStockQuantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("20000000");
        verify(publisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    void theSaleIsWrittenInTheStockLedgerWithANegativeChange() {
        service.createOrder(pickupRequest(1L, 2), aliceAuth);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).record(entries.capture(), eq(1000L), any(), isNull(), isNull());
        assertThat(entries.getValue()).hasSize(1);
        assertThat(entries.getValue().get(0).change()).isEqualTo(-2);
        assertThat(entries.getValue().get(0).reason()).isEqualTo(StockMovementReason.SALE);
        assertThat(entries.getValue().get(0).balanceAfter()).isEqualTo(3);
    }

    @Test
    void theBikesAreLockedBeforeTheyAreRead() {
        service.createOrder(pickupRequest(1L, 1), aliceAuth);
        verify(stockLedger).lock(anyList(), anyList());
    }

    @Test
    void discountPriceReplacesTheListPrice() {
        bike.setDiscountPrice(new BigDecimal("9000000"));
        service.createOrder(pickupRequest(1L, 2), aliceAuth);
        assertThat(saved().getSubtotal()).isEqualByComparingTo("18000000");
    }

    @Test
    void aColourAddsItsSurchargeAndTakesItsOwnStock() {
        ProductVariant red = OrderFixtures.colour(11L, bike, "Red", 2, "500000");
        OrderCreateRequest request = copy(pickupRequest(1L, 2), f -> f.items = List.of(new OrderCreateItemRequest(1L, 2, 11L)));
        service.createOrder(request, aliceAuth);
        assertThat(saved().getSubtotal()).isEqualByComparingTo("21000000");
        assertThat(red.getStockQuantity()).isZero();
        assertThat(bike.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void clientSentPricesAreIgnored() {
        OrderCreateRequest request = copy(pickupRequest(1L, 1), f -> {
            f.shippingFee = BigDecimal.ZERO;
            f.discountAmount = new BigDecimal("999999999");
        });
        service.createOrder(request, aliceAuth);
        assertThat(saved().getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(saved().getTotalAmount()).isEqualByComparingTo("10000000");
    }

    @Test
    void textFieldsAreNormalised() {
        OrderCreateRequest request = copy(pickupRequest(1L, 1), f -> {
            f.customerName = "  Nguyen Van A ";
            f.phoneNumber = " 0901.234-567 ";
            f.notes = "  giao nhanh  ";
        });
        service.createOrder(request, aliceAuth);
        Order order = saved();
        assertThat(order.getShipment().getRecipientName()).isEqualTo("Nguyen Van A");
        assertThat(order.getShipment().getPhoneNumber()).isEqualTo("0901234567");
        assertThat(order.getNotes()).isEqualTo("giao nhanh");
    }

    @Test
    void aGuestOrderHasNoAccount() {
        service.createOrder(pickupRequest(1L, 1), null);
        assertThat(saved().getUser()).isNull();
    }

    @Test
    void vnpayOrderHasNoPaymentRowUntilAPaymentUrlExists() {
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.paymentMethod = "vnpay"), aliceAuth);
        assertThat(saved().getPayment()).isNull();
    }

    @Test
    void blankPaymentMethodDefaultsToPayLater() {
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.paymentMethod = "  "), aliceAuth);
        assertThat(saved().getPayment().getPaymentMethod()).isEqualTo(PaymentMethod.PAY_LATER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASH", "BITCOIN", "PAY LATER", "1"})
    void unknownPaymentMethodsAreRejected(String method) {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.paymentMethod = method));
    }

    // --- request shape -------------------------------------------------------------------------------

    @Test
    void nullRequestAndEmptyItemsAre400() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(null, aliceAuth));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = null));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = List.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, 1000, Integer.MAX_VALUE})
    void quantityOutsideOneToTenIsRejected(int quantity) {
        assertRejected(pickupRequest(1L, quantity));
    }

    @Test
    void quantityBoundariesOneAndTenAreAccepted() {
        bike.setStockQuantity(10);
        service.createOrder(pickupRequest(1L, 10), aliceAuth);
        assertThat(bike.getStockQuantity()).isZero();
    }

    @Test
    void nullQuantityAndBadProductIdsAreRejected() {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = List.of(new OrderCreateItemRequest(1L, null, null))));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = List.of(new OrderCreateItemRequest(null, 1, null))));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = List.of(new OrderCreateItemRequest(0L, 1, null))));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = List.of(new OrderCreateItemRequest(-5L, 1, null))));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = java.util.Collections.singletonList(null)));
    }

    @Test
    void twentyLinesAreAllowedAndTwentyOneAreNot() {
        List<OrderCreateItemRequest> twentyOne = java.util.stream.IntStream.range(0, 21).mapToObj(i -> new OrderCreateItemRequest(1L, 1, null)).toList();
        assertRejected(copy(pickupRequest(1L, 1), f -> f.items = twentyOne));
        bike.setStockQuantity(100);
        List<OrderCreateItemRequest> twenty = java.util.stream.IntStream.range(0, 20).mapToObj(i -> new OrderCreateItemRequest(1L, 1, null)).toList();
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.items = twenty), aliceAuth);
        assertThat(bike.getStockQuantity()).isEqualTo(80);
    }

    @Test
    void unknownOrInactiveProductIs404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createOrder(pickupRequest(99L, 1), aliceAuth));
        bike.setActive(false);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createOrder(pickupRequest(1L, 1), aliceAuth));
    }

    // --- customer details ----------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Nguyen Van 2", "A1"})
    void badNamesAreRejected(String name) {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.customerName = name));
    }

    @Test
    void nameLengthBoundaryIs100() {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.customerName = "A".repeat(101)));
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.customerName = "A".repeat(100)), aliceAuth);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "12345", "1234567890", "090123456", "090123456789", "09012345ab", "+84901234567", "0901 234 56x"})
    void badPhonesAreRejected(String phone) {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.phoneNumber = phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0901234567", "09012345678", "0901 234 567", "0901-234-567", "0901.234.567"})
    void goodPhonesAreAccepted(String phone) {
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.phoneNumber = phone), aliceAuth);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "nope", "a@b", "a@b.c", "a b@c.com", "@c.com"})
    void badEmailsAreRejected(String email) {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.customerEmail = email));
    }

    @Test
    void notesBoundaryIs1000() {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.notes = "n".repeat(1001)));
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.notes = "n".repeat(1000)), aliceAuth);
    }

    @Test
    void addressBoundaryIs500AndMinimumTenForHomeDelivery() {
        assertRejected(copy(homeRequest(1L, 1, "Hà Nội"), f -> f.address = "a".repeat(501)));
        assertRejected(copy(homeRequest(1L, 1, "Hà Nội"), f -> f.address = "123456789"));
        assertRejected(copy(homeRequest(1L, 1, "Hà Nội"), f -> f.address = null));
        assertRejected(copy(homeRequest(1L, 1, "Hà Nội"), f -> f.address = "         "));
        service.createOrder(copy(homeRequest(1L, 1, "Hà Nội"), f -> f.address = "1234567890"), aliceAuth);
    }

    @Test
    void provinceIsRequiredForHomeDeliveryAndLimitedTo100() {
        assertRejected(homeRequest(1L, 1, null));
        assertRejected(homeRequest(1L, 1, "  "));
        assertRejected(homeRequest(1L, 1, "P".repeat(101)));
        service.createOrder(homeRequest(1L, 1, "P".repeat(100)), aliceAuth);
    }

    @Test
    void unknownDeliveryMethodIsRejected() {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.deliveryMethod = "DRONE"));
    }

    @Test
    void pickupNeedsAnActiveShowroom() {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.showroomId = null));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.showroomId = 0L));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.showroomId = -3L));
        when(showroomRepository.findById(2L)).thenReturn(Optional.of(OrderFixtures.showroom(2L, "Hà Nội", "Đống Đa", false)));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createOrder(copy(pickupRequest(1L, 1), f -> f.showroomId = 2L), aliceAuth));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.createOrder(copy(pickupRequest(1L, 1), f -> f.showroomId = 404L), aliceAuth));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void homeDeliveryOrderHasNoShowroomAndStoresTheAddress() {
        service.createOrder(homeRequest(1L, 1, "Hà Nội"), aliceAuth);
        assertThat(saved().getShipment().getPickupShowroom()).isNull();
        assertThat(saved().getShipment().getShippingAddress()).isEqualTo("12 Nguyen Hue, Ben Nghe, Hà Nội");
    }

    // --- stock ---------------------------------------------------------------------------------------

    @Test
    void ordersUpToTheLastBikeAreAcceptedAndThenStockIsZeroNeverNegative() {
        bike.setStockQuantity(2);
        service.createOrder(pickupRequest(1L, 2), aliceAuth);
        assertThat(bike.getStockQuantity()).isZero();
    }

    @Test
    void oneMoreThanInStockIsRefused() {
        bike.setStockQuantity(2);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(pickupRequest(1L, 3), aliceAuth));
        assertThat(bike.getStockQuantity()).isEqualTo(2);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void zeroOrNullStockIsSoldOut() {
        bike.setStockQuantity(0);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(pickupRequest(1L, 1), aliceAuth));
        bike.setStockQuantity(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(pickupRequest(1L, 1), aliceAuth));
    }

    @Test
    void twoLinesOfTheSameBikeShareItsStock() {
        bike.setStockQuantity(5);
        OrderCreateRequest request = copy(pickupRequest(1L, 1),
            f -> f.items = List.of(new OrderCreateItemRequest(1L, 3, null), new OrderCreateItemRequest(1L, 3, null)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(request, aliceAuth));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void variantStockIsCheckedSeparately() {
        ProductVariant red = OrderFixtures.colour(11L, bike, "Red", 1, null);
        OrderCreateRequest request = copy(pickupRequest(1L, 1), f -> f.items = List.of(new OrderCreateItemRequest(1L, 2, 11L)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(request, aliceAuth));
        assertThat(red.getStockQuantity()).isEqualTo(1);
    }

    @Test
    void aColourMustBeChosenWhenTheBikeHasActiveColours() {
        OrderFixtures.colour(11L, bike, "Red", 5, null);
        assertRejected(pickupRequest(1L, 1));
    }

    @Test
    void aColourOfAnotherBikeOrAnInactiveColourIsRejected() {
        ProductVariant red = OrderFixtures.colour(11L, bike, "Red", 5, null);
        OrderCreateRequest foreign = copy(pickupRequest(1L, 1), f -> f.items = List.of(new OrderCreateItemRequest(1L, 1, 999L)));
        assertRejected(foreign);
        red.setActive(false);
        OrderCreateRequest inactive = copy(pickupRequest(1L, 1), f -> f.items = List.of(new OrderCreateItemRequest(1L, 1, 11L)));
        assertRejected(inactive);
    }

    // --- promotions ----------------------------------------------------------------------------------

    @Test
    void percentageCodeIsAppliedAndItsUsageCounted() {
        Promotion promo = promotion(5L, "SAVE10", "PERCENTAGE", "10");
        service.createOrder(copy(pickupRequest(1L, 2), f -> f.discountCode = "SAVE10"), aliceAuth);
        assertThat(saved().getDiscountAmount()).isEqualByComparingTo("2000000");
        assertThat(saved().getTotalAmount()).isEqualByComparingTo("18000000");
        assertThat(saved().getPromotion()).isSameAs(promo);
        verify(promotionRepository).tryIncrementUsage(5L);
    }

    @Test
    void codeIsTrimmedAndLooksUpCaseInsensitively() {
        promotion(5L, "SAVE10", "PERCENTAGE", "10");
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "  SAVE10 "), aliceAuth);
        verify(promotionRepository).findByCodeIgnoreCase("SAVE10");
    }

    @Test
    void percentageRoundsHalfUpToWholeDong() {
        bike.setPrice(new BigDecimal("110"));
        promotion(5L, "FIVE", "PERCENTAGE", "5"); // 5.5
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "FIVE"), aliceAuth);
        assertThat(saved().getDiscountAmount()).isEqualByComparingTo("6");
        assertThat(saved().getTotalAmount()).isEqualByComparingTo("104");
    }

    @Test
    void percentageIsCappedByTheMaximumDiscount() {
        Promotion promo = promotion(5L, "BIG", "PERCENTAGE", "50");
        promo.setMaxDiscountAmount(new BigDecimal("1500000"));
        service.createOrder(copy(pickupRequest(1L, 2), f -> f.discountCode = "BIG"), aliceAuth);
        assertThat(saved().getDiscountAmount()).isEqualByComparingTo("1500000");
    }

    @Test
    void fixedDiscountNeverExceedsTheSubtotalAndTotalNeverGoesNegative() {
        promotion(5L, "HUGE", "FIXED_AMOUNT", "999999999");
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "HUGE"), aliceAuth);
        assertThat(saved().getDiscountAmount()).isEqualByComparingTo("10000000");
        assertThat(saved().getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void percentageOver100IsClampedToTheSubtotal() {
        promotion(5L, "CRAZY", "PERCENTAGE", "250");
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "CRAZY"), aliceAuth);
        assertThat(saved().getDiscountAmount()).isEqualByComparingTo("10000000");
    }

    @Test
    void unknownCodeIsRejectedAndNothingIsSold() {
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "NOPE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WAITING", "ENDED", "PAUSED", "live", ""})
    void onlyALivePromotionCanBeUsed(String status) {
        promotion(5L, "CODE", "PERCENTAGE", "10").setStatus(status);
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"));
        verify(promotionRepository, never()).tryIncrementUsage(anyLong());
    }

    @Test
    void promotionDatesAreRespected() {
        Promotion promo = promotion(5L, "CODE", "PERCENTAGE", "10");
        promo.setStartsAt(LocalDateTime.now().plusMinutes(1));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"));
        promo.setStartsAt(null);
        promo.setEndsAt(LocalDateTime.now().minusSeconds(1));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"));
        promo.setStartsAt(LocalDateTime.now().minusMinutes(1));
        promo.setEndsAt(LocalDateTime.now().plusMinutes(1));
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"), aliceAuth);
    }

    @Test
    void usageLimitBoundary() {
        Promotion promo = promotion(5L, "CODE", "PERCENTAGE", "10");
        promo.setUsageLimit(5);
        promo.setUsageCount(5);
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"));
        promo.setUsageCount(6);
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"));
        promo.setUsageCount(4);
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"), aliceAuth);
    }

    @Test
    void usageLimitZeroMeansUnlimited() {
        Promotion promo = promotion(5L, "CODE", "PERCENTAGE", "10");
        promo.setUsageLimit(0);
        promo.setUsageCount(100000);
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"), aliceAuth);
        assertThat(saved().getPromotion()).isSameAs(promo);
    }

    @Test
    void whenTheAtomicIncrementLosesTheRaceTheOrderIsRefused() {
        promotion(5L, "CODE", "PERCENTAGE", "10");
        when(promotionRepository.tryIncrementUsage(5L)).thenReturn(0);
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"));
    }

    @Test
    void aCustomerCannotUseTheSameCodeTwice() {
        promotion(5L, "CODE", "PERCENTAGE", "10");
        when(orderRepository.existsByUserIdAndPromotionIdAndStatusNot(1L, 5L, OrderStatus.CANCELLED)).thenReturn(true);
        assertRejected(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"));
        verify(promotionRepository, never()).tryIncrementUsage(anyLong());
    }

    @Test
    void aGuestIsLimitedByEmail() {
        promotion(5L, "CODE", "PERCENTAGE", "10");
        when(orderRepository.existsByUserIsNullAndCustomerEmailIgnoreCaseAndPromotionIdAndStatusNot("khach@example.com", 5L, OrderStatus.CANCELLED)).thenReturn(true);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder(copy(pickupRequest(1L, 1), f -> f.discountCode = "CODE"), null));
    }

    // --- shipping and registration fees --------------------------------------------------------------

    @Test
    void homeDeliveryInAProvinceWithAShowroomIsCharged200kPerBike() {
        when(showroomRepository.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of(OrderFixtures.showroom(1L, "Thành phố Hồ Chí Minh", "Quận 1", true)));
        service.createOrder(homeRequest(1L, 3, "TP. Hồ Chí Minh"), aliceAuth);
        assertThat(saved().getShippingFee()).isEqualByComparingTo("600000");
        assertThat(saved().getTotalAmount()).isEqualByComparingTo("30600000");
    }

    @Test
    void homeDeliveryElsewhereIsChargedTheOtherProvinceRate() {
        when(showroomRepository.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of(OrderFixtures.showroom(1L, "Hà Nội", "Cầu Giấy", true)));
        service.createOrder(homeRequest(1L, 2, "Cà Mau"), aliceAuth);
        assertThat(saved().getShippingFee()).isEqualByComparingTo("1600000");
    }

    @Test
    void provinceMatchingIgnoresAccentsCaseAndPrefixes() {
        when(showroomRepository.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of(OrderFixtures.showroom(1L, "Đà Nẵng", "Hải Châu", true)));
        service.createOrder(homeRequest(1L, 1, "  thanh pho da nang "), aliceAuth);
        assertThat(saved().getShippingFee()).isEqualByComparingTo("200000");
    }

    @Test
    void storePickupIsFreeOfShippingEvenWhenAProvinceIsSent() {
        service.createOrder(copy(pickupRequest(1L, 2), f -> f.province = "Cà Mau"), aliceAuth);
        assertThat(saved().getShippingFee()).isEqualByComparingTo("0");
    }

    @Test
    void registrationServiceIsChargedPerBikeThatNeedsAPlate() {
        Product moto = OrderFixtures.motorbike(2L, "30000000", 5);
        Product helmet = OrderFixtures.accessory(3L, "500000", 5);
        when(productRepository.findById(2L)).thenReturn(Optional.of(moto));
        when(productRepository.findById(3L)).thenReturn(Optional.of(helmet));
        OrderCreateRequest request = copy(pickupRequest(2L, 1), f -> {
            f.includeRegistration = true;
            f.items = List.of(new OrderCreateItemRequest(2L, 2, null), new OrderCreateItemRequest(3L, 4, null));
        });
        service.createOrder(request, aliceAuth);
        assertThat(saved().getRegistrationFee()).isEqualByComparingTo("1000000");
        assertThat(saved().getIncludeRegistrationService()).isTrue();
        assertThat(saved().getTotalAmount()).isEqualByComparingTo("63000000"); // 60,000,000 + 2,000,000 + 1,000,000
    }

    @Test
    void registrationServiceRefusedWhenTheShopDoesNotOfferIt() {
        Product moto = OrderFixtures.motorbike(2L, "30000000", 5);
        when(productRepository.findById(2L)).thenReturn(Optional.of(moto));
        when(pricingService.registrationServiceOffered()).thenReturn(false);
        assertRejected(copy(pickupRequest(2L, 1), f -> f.includeRegistration = true));
    }

    @Test
    void registrationServiceRefusedForHomeDeliveryAndForBikesWithoutAPlate() {
        Product moto = OrderFixtures.motorbike(2L, "30000000", 5);
        when(productRepository.findById(2L)).thenReturn(Optional.of(moto));
        assertRejected(copy(homeRequest(2L, 1, "Hà Nội"), f -> f.includeRegistration = true));
        assertRejected(copy(pickupRequest(1L, 1), f -> f.includeRegistration = true)); // a 25 km/h e-bike needs no plate
    }

    @Test
    void notTickingRegistrationNeverChargesIt() {
        Product moto = OrderFixtures.motorbike(2L, "30000000", 5);
        when(productRepository.findById(2L)).thenReturn(Optional.of(moto));
        service.createOrder(copy(pickupRequest(2L, 1), f -> f.includeRegistration = false), aliceAuth);
        assertThat(saved().getRegistrationFee()).isEqualByComparingTo("0");
    }

    // --- email OTP and instalments -------------------------------------------------------------------

    @Test
    void whenOtpIsRequiredTheVerificationIsConsumedWithTheRequestEmail() {
        ReflectionTestUtils.setField(service, "emailOtpRequired", true);
        UUID session = UUID.randomUUID();
        service.createOrder(copy(pickupRequest(1L, 1), f -> f.verificationId = session), aliceAuth);
        verify(emailVerification).assertVerifiedForOrder(session, "Khach@Example.com");
    }

    @Test
    void whenOtpIsRequiredAndFailsNothingIsSold() {
        ReflectionTestUtils.setField(service, "emailOtpRequired", true);
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(emailVerification).assertVerifiedForOrder(any(), any());
        assertRejected(pickupRequest(1L, 1));
    }

    @Test
    void whenOtpIsNotRequiredTheVerificationIsNotTouched() {
        service.createOrder(pickupRequest(1L, 1), aliceAuth);
        verify(emailVerification, never()).assertVerifiedForOrder(any(), any());
    }

    @Test
    void installmentOrderPaysOnlyTheDownPaymentAndIsRecorded() {
        InstallmentRules.Plan plan = new InstallmentRules.Plan(new BigDecimal("10000000"), 20, new BigDecimal("2000000"), new BigDecimal("8000000"),
            12, new BigDecimal("10"), new BigDecimal("400000"), new BigDecimal("700000"), new BigDecimal("10400000"));
        InstallmentPlanner.Prepared prepared = new InstallmentPlanner.Prepared(plan, "1234", new BigDecimal("20000000"));
        when(installmentPlanner.prepare(any(), any(), any())).thenReturn(prepared);

        service.createOrder(copy(pickupRequest(1L, 1), f -> f.paymentMethod = "installment"), aliceAuth);

        assertThat(saved().getPayment().getPaymentMethod()).isEqualTo(PaymentMethod.INSTALLMENT);
        assertThat(saved().getPayment().getAmount()).isEqualByComparingTo("2000000");
        verify(installmentPlanner).prepare(eq(alice), eq(new BigDecimal("10000000")), any());
        verify(installmentPlanner).record(any(Order.class), eq(alice), eq(prepared));
        verify(userRepository).findLockedByUsername("alice");
    }

    @Test
    void staffAccountsCannotApplyForCredit() {
        Authentication both = OrderFixtures.auth("alice", "ROLE_CUSTOMER", "ROLE_MANAGER");
        assertStatus(HttpStatus.FORBIDDEN, () -> service.createOrder(copy(pickupRequest(1L, 1), f -> f.paymentMethod = "INSTALLMENT"), both));
        verify(installmentPlanner, never()).record(any(), any(), any());
    }
}
