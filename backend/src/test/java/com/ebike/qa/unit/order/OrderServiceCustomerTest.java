package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.GuestOrderLinkRequest;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.response.GuestOrderLinkResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.VehicleType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceCustomerTest extends OrderServiceTestBase {

    Product accessory;
    User alice;
    User bob;
    Authentication aliceAuth;
    Authentication bobAuth;
    Authentication staffAuth;

    @BeforeEach
    void data() {
        accessory = OrderFixtures.accessory(2L, "100000", 10);
        alice = OrderFixtures.user(1L, "alice", "Alice@Example.com");
        bob = OrderFixtures.user(2L, "bob", "bob@example.com");
        aliceAuth = OrderFixtures.customer("alice");
        bobAuth = OrderFixtures.customer("bob");
        staffAuth = OrderFixtures.staff("staffy");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(userRepository.findByUsername("staffy")).thenReturn(Optional.of(OrderFixtures.user(9L, "staffy", "s@x.com")));
    }

    private Order order(long id, OrderStatus status, User owner) {
        Order order = OrderFixtures.order(id, "ORD-" + id, status, owner);
        OrderFixtures.line(order, id * 10, accessory, null, 2, "100000");
        OrderFixtures.payment(order, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "200000");
        OrderFixtures.shipment(order, null);
        when(orderRepository.findLockedById(id)).thenReturn(Optional.of(order));
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        return order;
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    // --- cancellation by the customer ----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PROCESSING"})
    void ownerCanCancelWhileTheOrderHasNotLeftTheStore(OrderStatus status) {
        Order order = order(10, status, alice);
        accessory.setStockQuantity(8);
        OrderResponse response = service.requestCancellation(10L, new OrderCancellationRequest("changed my mind", null), aliceAuth);
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(order.getCancellationReason()).isEqualTo("changed my mind");
        assertThat(order.getCancellationRequestedBy()).isSameAs(alice);
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        // the customer cancelling their own order is not a staff action on the ledger
        verify(stockLedger).record(any(), eq(10L), any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"READY_FOR_PICKUP", "SHIPPED", "DELIVERED", "CANCELLED"})
    void ownerCannotCancelOnceTheBikeIsReadyShippedDeliveredOrAlreadyCancelled(OrderStatus status) {
        Order order = order(10, status, alice);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCancellation(10L, new OrderCancellationRequest("x", null), aliceAuth));
        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void doubleSubmitRestocksOnlyOnce() {
        order(10, OrderStatus.PENDING, alice);
        accessory.setStockQuantity(8);
        service.requestCancellation(10L, new OrderCancellationRequest("x", null), aliceAuth);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCancellation(10L, new OrderCancellationRequest("x", null), aliceAuth));
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void anotherCustomerAndGuestOrdersAreForbidden() {
        order(10, OrderStatus.PENDING, alice);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.requestCancellation(10L, new OrderCancellationRequest("x", null), bobAuth));
        order(11, OrderStatus.PENDING, null);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.requestCancellation(11L, new OrderCancellationRequest("x", null), aliceAuth));
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void staffCannotUseTheCustomerCancelEndpointOnSomeoneElsesOrder() {
        order(10, OrderStatus.PENDING, alice);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.requestCancellation(10L, new OrderCancellationRequest("x", null), staffAuth));
    }

    @Test
    void anonymousIs401AndUnknownOrderIs404() {
        order(10, OrderStatus.PENDING, alice);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.requestCancellation(10L, new OrderCancellationRequest("x", null), null));
        when(orderRepository.findLockedById(404L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.requestCancellation(404L, new OrderCancellationRequest("x", null), aliceAuth));
    }

    @Test
    void anUnknownAccountBehindAValidTokenIs401() {
        order(10, OrderStatus.PENDING, alice);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.requestCancellation(10L, new OrderCancellationRequest("x", null), OrderFixtures.customer("ghost")));
    }

    @Test
    void aReasonIsRequiredAndLimitedTo1000() {
        Order order = order(10, OrderStatus.PENDING, alice);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCancellation(10L, null, aliceAuth));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCancellation(10L, new OrderCancellationRequest("   ", null), aliceAuth));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCancellation(10L, new OrderCancellationRequest("r".repeat(1001), null), aliceAuth));
        service.requestCancellation(10L, new OrderCancellationRequest("r".repeat(1000), null), aliceAuth);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void aShipmentAlreadyOnTheRoadBlocksCancellationEvenIfTheOrderStatusLags() {
        for (ShipmentStatus status : List.of(ShipmentStatus.SHIPPED, ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED)) {
            Order order = order(10, OrderStatus.PROCESSING, alice);
            order.getShipment().setShipmentStatus(status);
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCancellation(10L, new OrderCancellationRequest("x", null), aliceAuth));
            assertThat(accessory.getStockQuantity()).isEqualTo(10);
        }
    }

    @Test
    void aPreparingShipmentDoesNotBlockCancellation() {
        Order order = order(10, OrderStatus.PROCESSING, alice);
        order.getShipment().setShipmentStatus(ShipmentStatus.PREPARING);
        service.requestCancellation(10L, new OrderCancellationRequest("x", null), aliceAuth);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void aPaidOrderCancelledByTheCustomerKeepsItsPaidPaymentForTheRefund() {
        Order order = order(10, OrderStatus.CONFIRMED, alice);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        service.requestCancellation(10L, new OrderCancellationRequest("x", null), aliceAuth);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(accessory.getStockQuantity()).isEqualTo(12);
    }

    // --- guest order linking -------------------------------------------------------------------------

    @Test
    void linkingNeedsAVerifiedEmailOfTheAccountAndTakesOnlyMatchingGuestOrders() {
        Order guest1 = order(20, OrderStatus.PENDING, null);
        Order guest2 = order(21, OrderStatus.CONFIRMED, null);
        when(orderRepository.findByUserIsNullAndCustomerEmail("alice@example.com")).thenReturn(List.of(guest1, guest2));
        UUID session = UUID.randomUUID();

        GuestOrderLinkResponse response = service.linkGuestOrders(new GuestOrderLinkRequest(session), aliceAuth);

        verify(emailVerification).assertVerifiedForOrder(session, "Alice@Example.com");
        assertThat(response.linkedOrders()).isEqualTo(2);
        assertThat(guest1.getUser()).isSameAs(alice);
        assertThat(guest2.getUser()).isSameAs(alice);
    }

    @Test
    void anUnverifiedEmailLinksNothing() {
        Order guest = order(20, OrderStatus.PENDING, null);
        when(orderRepository.findByUserIsNullAndCustomerEmail("alice@example.com")).thenReturn(List.of(guest));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(emailVerification).assertVerifiedForOrder(any(), any());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.linkGuestOrders(new GuestOrderLinkRequest(null), aliceAuth));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.linkGuestOrders(null, aliceAuth));
        assertThat(guest.getUser()).isNull();
        verify(orderRepository, never()).saveAll(any());
    }

    @Test
    void linkingAsAnAnonymousVisitorIs401() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.linkGuestOrders(new GuestOrderLinkRequest(UUID.randomUUID()), null));
    }

    @Test
    void noMatchingGuestOrdersLinksZero() {
        when(orderRepository.findByUserIsNullAndCustomerEmail("alice@example.com")).thenReturn(List.of());
        assertThat(service.linkGuestOrders(new GuestOrderLinkRequest(UUID.randomUUID()), aliceAuth).linkedOrders()).isZero();
    }

    // --- reading orders ------------------------------------------------------------------------------

    @Test
    void ownerAndStaffMayReadAnOrderButAnotherCustomerMayNot() {
        order(10, OrderStatus.PENDING, alice);
        assertThat(service.getOrderById(10L, aliceAuth).id()).isEqualTo(10L);
        assertThat(service.getOrderById(10L, staffAuth).id()).isEqualTo(10L);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getOrderById(10L, bobAuth));
    }

    @Test
    void aGuestOrderCanNotBeReadByAnyCustomerAccountButStaffCan() {
        order(11, OrderStatus.PENDING, null);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.getOrderById(11L, aliceAuth));
        assertThat(service.getOrderById(11L, staffAuth).id()).isEqualTo(11L);
    }

    @Test
    void readingAsAnonymousIs401AndMissingOrderIs404() {
        order(10, OrderStatus.PENDING, alice);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.getOrderById(10L, null));
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getOrderById(404L, aliceAuth));
    }

    @Test
    void aCustomerListNeverContainsAnotherCustomersOrdersEvenWhenAskingForTheirUserId() {
        Order mine = order(10, OrderStatus.PENDING, alice);
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(mine));
        Order bobs = order(11, OrderStatus.PENDING, bob);
        when(orderRepository.findByUserId(2L)).thenReturn(List.of(bobs));
        List<OrderResponse> result = service.getOrders(aliceAuth, 2L, null, null, null, null, null);
        assertThat(result).extracting(OrderResponse::id).containsExactly(10L);
        verify(orderRepository, never()).findByUserId(2L);
        verify(orderRepository, never()).findAll();
    }

    @Test
    void staffSeesEverythingOrOneCustomer() {
        Order a = order(10, OrderStatus.PENDING, alice);
        Order b = order(11, OrderStatus.PENDING, bob);
        when(orderRepository.findAll()).thenReturn(List.of(a, b));
        when(orderRepository.findByUserId(2L)).thenReturn(List.of(b));
        assertThat(service.getOrders(staffAuth, null, null, null, null, null, null)).hasSize(2);
        assertThat(service.getOrders(staffAuth, 2L, null, null, null, null, null)).extracting(OrderResponse::id).containsExactly(11L);
    }

    @Test
    void anonymousListIs401() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.getOrders(null, null, null, null, null, null, null));
    }

    @Test
    void listIsNewestFirstAndLimitKeepsTheNewest() {
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Order order = order(100 + i, OrderStatus.PENDING, alice);
            ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 1, i, 10, 0));
            orders.add(order);
        }
        when(orderRepository.findByUserId(1L)).thenReturn(orders);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null, 2)).extracting(OrderResponse::id).containsExactly(105L, 104L);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null, null)).extracting(OrderResponse::id)
            .containsExactly(105L, 104L, 103L, 102L, 101L);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null)).hasSize(5);
    }

    @Test
    void limitIsCappedAt2000() {
        List<Order> orders = IntStream.range(0, 2105).mapToObj(i -> {
            Order order = OrderFixtures.order(1000L + i, "N" + i, OrderStatus.PENDING, alice);
            OrderFixtures.line(order, 1L, accessory, null, 1, "1");
            return order;
        }).toList();
        when(orderRepository.findByUserId(1L)).thenReturn(orders);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null, 2001)).hasSize(2000);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null, Integer.MAX_VALUE)).hasSize(2000);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null, 2000)).hasSize(2000);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null, 1)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void regression_limitBelowOneReturnsEveryOrder(int limit) {
        // BUG (Low): OrderServiceImpl.java:186 `.limit(limit == null || limit < 1 ? Long.MAX_VALUE : ...)`. OrderService Javadoc: "The newest
        // {limit} orders that match (all of them when limit is null)". limit=0 or negative is not null, so it must not mean "everything":
        // expected 0 orders or a 400, actual the whole table (up to the 2000 cap does not apply here either).
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            orders.add(order(200 + i, OrderStatus.PENDING, alice));
        }
        when(orderRepository.findByUserId(1L)).thenReturn(orders);
        List<OrderResponse> result;
        try {
            result = service.getOrders(aliceAuth, null, null, null, null, null, limit);
        } catch (ResponseStatusException rejected) {
            return; // a 400 is an acceptable answer
        }
        assertThat(result).isEmpty();
    }

    @Test
    void filtersByOrderAndPaymentStatus() {
        Order pending = order(10, OrderStatus.PENDING, alice);
        Order cancelled = order(11, OrderStatus.CANCELLED, alice);
        cancelled.getPayment().setPaymentStatus(PaymentStatus.CANCELLED);
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(pending, cancelled));
        assertThat(service.getOrders(aliceAuth, null, " cancelled ", null, null, null, null)).extracting(OrderResponse::id).containsExactly(11L);
        assertThat(service.getOrders(aliceAuth, null, null, "pending", null, null, null)).extracting(OrderResponse::id).containsExactly(10L);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.getOrders(aliceAuth, null, "BOGUS", null, null, null, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.getOrders(aliceAuth, null, null, "BOGUS", null, null, null));
    }

    @Test
    void paymentStatusFilterHidesOrdersWithoutAPaymentRow() {
        Order vnpayWithoutPayment = order(10, OrderStatus.PENDING, alice);
        vnpayWithoutPayment.setPayment(null);
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(vnpayWithoutPayment));
        assertThat(service.getOrders(aliceAuth, null, null, "PENDING", null, null, null)).isEmpty();
        assertThat(service.getOrders(aliceAuth, null, null, null, null, null, null)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ord-10", "ALICE@example", "0901234567", "nguyen van", "frame-0001", "frame 0001", "engine0001"})
    void searchFindsByNumberEmailPhoneNameOrFrameAndEngineNumber(String term) {
        Order order = order(10, OrderStatus.PENDING, alice);
        order.setCustomerEmail("alice@example.com");
        Product bike = OrderFixtures.bike(1L, "X", "1", 1, VehicleType.E_BIKE);
        com.ebike.model.entity.order.OrderItem line = OrderFixtures.line(order, 999L, bike, null, 1, "1");
        OrderFixtures.unit(line, 1L, "FRAME-0001", "ENGINE-0001");
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(order));
        assertThat(service.getOrders(aliceAuth, null, null, null, term, null, null)).hasSize(1);
    }

    @Test
    void searchThatMatchesNothingReturnsNothingAndPunctuationOnlyDoesNotMatchEverythingByUnit() {
        Order order = order(10, OrderStatus.PENDING, alice);
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(order));
        assertThat(service.getOrders(aliceAuth, null, null, null, "zzz-not-there", null, null)).isEmpty();
        assertThat(service.getOrders(aliceAuth, null, null, null, "---", null, null)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void blankSearchMatchesEverything(String term) {
        Order order = order(10, OrderStatus.PENDING, alice);
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(order));
        assertThat(service.getOrders(aliceAuth, null, null, null, term, null, null)).hasSize(1);
    }

    @Test
    void showroomFilter() {
        Order pickup = order(10, OrderStatus.PENDING, alice);
        pickup.getShipment().setPickupShowroom(OrderFixtures.showroom(7L, "Hà Nội", "Cầu Giấy", true));
        Order home = order(11, OrderStatus.PENDING, alice);
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(pickup, home));
        assertThat(service.getOrders(aliceAuth, null, null, null, null, 7L, null)).extracting(OrderResponse::id).containsExactly(10L);
        assertThat(service.getOrders(aliceAuth, null, null, null, null, 8L, null)).isEmpty();
    }
}
