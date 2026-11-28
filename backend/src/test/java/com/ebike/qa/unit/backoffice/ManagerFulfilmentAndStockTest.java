package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.manager.request.ManagerPickupDeadlineRequest;
import com.ebike.dto.manager.request.ManagerProductStockUpdateRequest;
import com.ebike.dto.manager.request.ManagerShipmentUpdateRequest;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.service.stock.StockLedger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ManagerFulfilmentAndStockTest extends ManagerBase {

    @BeforeEach
    void stubs() {
        when(orders.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(products.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(productService.toSummaryDto(any())).thenReturn(null);
    }

    private Order delivery(OrderStatus status, ShipmentStatus shipmentStatus) {
        Order o = Fx.order(1L, "ORD-1", status, LocalDateTime.of(2025, 3, 1, 9, 0));
        Fx.shipment(o, shipmentStatus);
        // a hand-over needs the money in (M-20): the orders here start paid unless a test says otherwise
        Fx.payment(7L, o, com.ebike.model.entity.order.PaymentMethod.PAY_LATER, com.ebike.model.entity.order.PaymentStatus.PAID, "1000", LocalDateTime.of(2025, 3, 1, 9, 0));
        when(orders.findLockedById(1L)).thenReturn(Optional.of(o));
        return o;
    }

    private Order pickup(OrderStatus status) {
        Order o = delivery(status, ShipmentStatus.PENDING);
        o.getShipment().setPickupShowroom(new Showroom());
        return o;
    }

    private static ManagerShipmentUpdateRequest ship(String status, String tracking) {
        return new ManagerShipmentUpdateRequest(status, tracking);
    }

    // ------------------------------------------------------------------ shipment: validation

    @Test
    void shipment_nullOrBlankStatus_is400() {
        delivery(OrderStatus.PROCESSING, ShipmentStatus.PREPARING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship(null, null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("  ", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void shipment_unknownOrder_is404() {
        assertThatThrownBy(() -> service.updateOrderShipment(404L, ship("SHIPPED", null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void shipment_cancelledOrder_is400_andNothingChanges() {
        Order o = delivery(OrderStatus.CANCELLED, ShipmentStatus.PENDING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("PREPARING", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.PENDING);
        verify(orders, never()).save(any());
    }

    @Test
    void shipment_orderWithoutShipment_is400() {
        Order o = Fx.order(1L, "ORD-1", OrderStatus.PROCESSING, LocalDateTime.now());
        when(orders.findLockedById(1L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("SHIPPED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void shipment_unknownStatusText_is400() {
        delivery(OrderStatus.PROCESSING, ShipmentStatus.PREPARING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("TELEPORTED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void shipment_statusIsTrimmedAndCaseInsensitive() {
        Order o = delivery(OrderStatus.PENDING, ShipmentStatus.PENDING);
        service.updateOrderShipment(1L, ship("  preparing ", null));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.PREPARING);
    }

    // ------------------------------------------------------------------ shipment: transitions

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED"})
    void shipment_preparing_movesPendingOrConfirmedOrderToProcessing(OrderStatus before) {
        Order o = delivery(before, ShipmentStatus.PENDING);
        service.updateOrderShipment(1L, ship("PREPARING", null));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(audit).record(any(), any());
    }

    @Test
    void shipment_preparing_doesNotPullAShippedOrderBack() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        try {
            service.updateOrderShipment(1L, ship("PREPARING", null));
        } catch (org.springframework.web.server.ResponseStatusException refused) {
            return;
        }
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shipment_shipped_fromProcessing_setsShippedAtTrackingAndOrderStatus() {
        Order o = delivery(OrderStatus.PROCESSING, ShipmentStatus.PREPARING);
        var res = service.updateOrderShipment(1L, ship("SHIPPED", "  GHN123 "));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(o.getShipment().getShippedAt()).isNotNull();
        assertThat(o.getShipment().getTrackingNumber()).isEqualTo("GHN123");
        assertThat(res.status()).isEqualTo("SHIPPED");
        assertThat(res.shipment().trackingNumber()).isEqualTo("GHN123");
    }

    @Test
    void shipment_inTransit_keepsEarlierShippedAt() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        LocalDateTime earlier = LocalDateTime.of(2025, 3, 2, 8, 0);
        o.getShipment().setShippedAt(earlier);
        service.updateOrderShipment(1L, ship("IN_TRANSIT", null));
        assertThat(o.getShipment().getShippedAt()).isEqualTo(earlier);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "READY_FOR_PICKUP", "DELIVERED"})
    void shipment_shipped_onlyFromProcessingOrShipped(OrderStatus before) {
        Order o = delivery(before, ShipmentStatus.PREPARING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("SHIPPED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getStatus()).isEqualTo(before);
    }

    @Test
    void shipment_storePickupOrder_cannotBeSentWithACarrier() {
        Order o = pickup(OrderStatus.PROCESSING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("SHIPPED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("IN_TRANSIT", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void shipment_delivered_fromShipped_setsBothTimestampsAndOrderDelivered() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        service.updateOrderShipment(1L, ship("DELIVERED", null));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o.getShipment().getDeliveredAt()).isNotNull();
        assertThat(o.getShipment().getShippedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PROCESSING", "READY_FOR_PICKUP"})
    void shipment_delivered_deliveryOrderMustHaveBeenShippedFirst(OrderStatus before) {
        Order o = delivery(before, ShipmentStatus.PREPARING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("DELIVERED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getStatus()).isEqualTo(before);
        assertThat(o.getShipment().getDeliveredAt()).isNull();
    }

    @Test
    void shipment_delivered_pickupOrder_onlyFromReadyForPickup() {
        Order o = pickup(OrderStatus.PROCESSING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("DELIVERED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        Order ready = pickup(OrderStatus.READY_FOR_PICKUP);
        service.updateOrderShipment(1L, ship("DELIVERED", null));
        assertThat(ready.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void shipment_delivered_twice_keepsTheFirstDeliveryTime() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        service.updateOrderShipment(1L, ship("DELIVERED", null));
        LocalDateTime first = o.getShipment().getDeliveredAt();
        // the shipment is locked once the order is delivered (M-18): a second save is refused and nothing moves
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("DELIVERED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getDeliveredAt()).isEqualTo(first);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void shipment_blankTracking_clearsIt_nullTrackingKeepsIt() {
        Order o = delivery(OrderStatus.PROCESSING, ShipmentStatus.PREPARING);
        o.getShipment().setTrackingNumber("OLD");
        service.updateOrderShipment(1L, ship("PREPARING", null));
        assertThat(o.getShipment().getTrackingNumber()).isEqualTo("OLD");
        service.updateOrderShipment(1L, ship("PREPARING", "   "));
        assertThat(o.getShipment().getTrackingNumber()).isNull();
    }

    private Order withUnits(OrderStatus status, int quantity, int unitsRecorded) {
        Order o = delivery(status, ShipmentStatus.PREPARING);
        Product p = Fx.product(5L, "Amy");
        ProductSpecification spec = new ProductSpecification();
        spec.setVehicleType(VehicleType.E_SCOOTER);
        p.setSpecification(spec);
        OrderItem item = Fx.item(1L, o, p, quantity, "100");
        for (int i = 0; i < unitsRecorded; i++) {
            OrderItemUnit u = new OrderItemUnit();
            u.setOrderItem(item);
            u.setFrameNumber("F" + i);
            u.setEngineNumber("E" + i);
            item.getUnits().add(u);
        }
        return o;
    }

    @Test
    void shipment_vehicleLeavingTheStore_needsFrameAndEngineNumbersOfEveryBike() {
        Order o = withUnits(OrderStatus.PROCESSING, 2, 1);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("SHIPPED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void shipment_allNumbersRecorded_shippingIsAllowed() {
        Order o = withUnits(OrderStatus.PROCESSING, 2, 2);
        service.updateOrderShipment(1L, ship("SHIPPED", null));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shipment_batteriesAndAccessories_needNoNumbers() {
        Order o = delivery(OrderStatus.PROCESSING, ShipmentStatus.PREPARING);
        Product p = Fx.product(5L, "Battery");
        ProductSpecification spec = new ProductSpecification();
        spec.setVehicleType(VehicleType.BATTERY);
        p.setSpecification(spec);
        Fx.item(1L, o, p, 3, "100");
        service.updateOrderShipment(1L, ship("SHIPPED", null));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /**
     * Once the bike is delivered the shipment cannot go back to PREPARING/PENDING: that leaves a DELIVERED order with a shipment that says
     * it is not even packed (the order status is only synced forwards, ManagerServiceImpl.java:768-780). Expected 400; actual: accepted.
     */
    @Test
    void regression_shipment_canBeMovedBackToPreparingAfterDelivery() {
        Order o = delivery(OrderStatus.DELIVERED, ShipmentStatus.DELIVERED);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("PREPARING", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void regression_shipment_canBeSetBackToPendingForAnOrderThatIsAlreadyShipped() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("PENDING", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.SHIPPED);
    }

    // ------------------------------------------------------------------ B5: forward-only shipment, payment before hand-over, returns

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"PENDING", "PREPARING", "SHIPPED", "IN_TRANSIT", "DELIVERED", "RETURNED"})
    void shipment_aDeliveredOrderIsLocked(ShipmentStatus next) {
        Order o = delivery(OrderStatus.DELIVERED, ShipmentStatus.DELIVERED);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship(next.name(), "TRACK"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(orders, never()).save(any());
        verify(orderService, never()).cancelReturnedOrder(any(), any());
    }

    @Test
    void shipment_inTransitCannotGoBackToShipped() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.IN_TRANSIT);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("SHIPPED", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void shipment_preparingCannotGoBackToPending() {
        Order o = delivery(OrderStatus.PROCESSING, ShipmentStatus.PREPARING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("PENDING", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.PREPARING);
    }

    @Test
    void shipment_sameStatusAgainStillSavesTheTrackingNumber() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        service.updateOrderShipment(1L, ship("SHIPPED", "GHN9"));
        assertThat(o.getShipment().getTrackingNumber()).isEqualTo("GHN9");
    }

    @Test
    void shipment_deliveredIsRefusedWhileThePaymentIsNotPaid_andAllowedOnceItIs() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        Fx.payment(7L, o, com.ebike.model.entity.order.PaymentMethod.PAY_LATER, com.ebike.model.entity.order.PaymentStatus.PENDING, "1000", null);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("DELIVERED", null))).satisfies(t -> {
            assertStatus(t, HttpStatus.BAD_REQUEST);
            assertThat(((org.springframework.web.server.ResponseStatusException) t).getReason()).contains("Xác nhận đã thu");
        });
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        o.getPayment().setPaymentStatus(com.ebike.model.entity.order.PaymentStatus.PAID);
        service.updateOrderShipment(1L, ship("DELIVERED", null));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    private static ManagerShipmentUpdateRequest returned(String reason) {
        return new ManagerShipmentUpdateRequest("RETURNED", null, reason);
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"SHIPPED", "IN_TRANSIT"})
    void shipment_returned_cancelsTheOrderThroughTheCancelPath(ShipmentStatus before) {
        Order o = delivery(OrderStatus.SHIPPED, before);
        service.updateOrderShipment(1L, returned("  Khách từ chối nhận  "));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.RETURNED);
        verify(orderService).cancelReturnedOrder(o, "  Khách từ chối nhận  ");
        verify(orders).save(o);
        verify(audit).record(any(), org.mockito.ArgumentMatchers.contains("Khách từ chối nhận"));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PROCESSING"})
    void shipment_returned_refusedForAnOrderThatNeverLeftTheStore(OrderStatus before) {
        Order o = delivery(before, ShipmentStatus.PREPARING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, returned("x"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getStatus()).isEqualTo(before);
        verify(orderService, never()).cancelReturnedOrder(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PROCESSING", "READY_FOR_PICKUP"})
    void shipment_returned_refusedForAStorePickupOrder(OrderStatus before) {
        Order o = pickup(before);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, returned("x"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getStatus()).isEqualTo(before);
        verify(orderService, never()).cancelReturnedOrder(any(), any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"   "})
    void shipment_returned_needsAReason(String reason) {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, returned(reason))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        verify(orderService, never()).cancelReturnedOrder(any(), any());
    }

    @Test
    void shipment_returned_isAcceptedWhileThePaymentIsStillUnpaid() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.IN_TRANSIT);
        o.getPayment().setPaymentStatus(com.ebike.model.entity.order.PaymentStatus.PENDING);
        service.updateOrderShipment(1L, returned("Khách từ chối nhận hàng thu tiền"));
        assertThat(o.getShipment().getShipmentStatus()).isEqualTo(ShipmentStatus.RETURNED);
        verify(orderService).cancelReturnedOrder(o, "Khách từ chối nhận hàng thu tiền");
    }

    @Test
    void shipment_delivered_withoutAnyPaymentRow_isRefusedWithTheOnlineWording() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        o.setPayment(null);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("DELIVERED", null))).satisfies(t -> {
            assertStatus(t, HttpStatus.BAD_REQUEST);
            assertThat(((org.springframework.web.server.ResponseStatusException) t).getReason()).contains("thanh toán trực tuyến");
        });
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shipment_delivered_unpaidVnpay_isRefusedWithTheOnlineWording() {
        Order o = delivery(OrderStatus.SHIPPED, ShipmentStatus.SHIPPED);
        o.getPayment().setPaymentMethod(com.ebike.model.entity.order.PaymentMethod.VNPAY);
        o.getPayment().setPaymentStatus(com.ebike.model.entity.order.PaymentStatus.PENDING);
        assertThatThrownBy(() -> service.updateOrderShipment(1L, ship("DELIVERED", null))).satisfies(t -> {
            assertStatus(t, HttpStatus.BAD_REQUEST);
            assertThat(((org.springframework.web.server.ResponseStatusException) t).getReason()).contains("thanh toán trực tuyến");
        });
    }

    // ------------------------------------------------------------------ pickup deadline

    private static ManagerPickupDeadlineRequest at(LocalDateTime t) {
        return new ManagerPickupDeadlineRequest(t);
    }

    @Test
    void deadline_nullRequestOrDate_is400() {
        assertThatThrownBy(() -> service.updatePickupDeadline(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updatePickupDeadline(1L, at(null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void deadline_unknownOrder_is404() {
        assertThatThrownBy(() -> service.updatePickupDeadline(1L, at(LocalDateTime.now().plusDays(1)))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"})
    void deadline_onlyForOrdersWaitingForPickup(OrderStatus status) {
        Order o = Fx.order(1L, "ORD-1", status, LocalDateTime.now());
        when(orders.findById(1L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.updatePickupDeadline(1L, at(LocalDateTime.now().plusDays(1)))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void deadline_mustBeInTheFuture_nowAndPastRefused() {
        Order o = Fx.order(1L, "ORD-1", OrderStatus.READY_FOR_PICKUP, LocalDateTime.now());
        when(orders.findById(1L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.updatePickupDeadline(1L, at(LocalDateTime.now().minusSeconds(1)))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updatePickupDeadline(1L, at(LocalDateTime.now().minusDays(5)))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(orders, never()).save(any());
    }

    @Test
    void deadline_future_isSaved_audited_andNotifiesAccountHolder() {
        Order o = Fx.order(1L, "ORD-1", OrderStatus.READY_FOR_PICKUP, LocalDateTime.now());
        o.setUser(Fx.user(3L, "c", "c@x.com", "CUSTOMER"));
        when(orders.findById(1L)).thenReturn(Optional.of(o));
        LocalDateTime target = LocalDateTime.now().plusDays(2);
        var res = service.updatePickupDeadline(1L, at(target));
        assertThat(o.getPickupDeadlineAt()).isEqualTo(target);
        assertThat(res.pickupDeadlineAt()).isEqualTo(target);
        verify(audit).record(eq("đổi hạn giữ xe"), any());
    }

    @Test
    void deadline_sameValueAsBefore_isANoOp_noSaveNoAudit() {
        Order o = Fx.order(1L, "ORD-1", OrderStatus.READY_FOR_PICKUP, LocalDateTime.now());
        LocalDateTime target = LocalDateTime.now().plusDays(2);
        o.setPickupDeadlineAt(target);
        when(orders.findById(1L)).thenReturn(Optional.of(o));
        service.updatePickupDeadline(1L, at(target));
        verify(orders, never()).save(any());
        verify(audit, never()).record(any(), any());
    }

    @Test
    void deadline_guestOrder_isSavedWithoutNotification() {
        Order o = Fx.order(1L, "ORD-1", OrderStatus.READY_FOR_PICKUP, LocalDateTime.now());
        when(orders.findById(1L)).thenReturn(Optional.of(o));
        service.updatePickupDeadline(1L, at(LocalDateTime.now().plusHours(5)));
        assertThat(o.getPickupDeadlineAt()).isNotNull();
    }

    // ------------------------------------------------------------------ product stock

    private Product plainProduct(int stock) {
        Product p = Fx.product(1L, "Amy");
        p.setStockQuantity(stock);
        when(products.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    private static ManagerProductStockUpdateRequest stock(Integer qty, String note) {
        return new ManagerProductStockUpdateRequest(qty, note);
    }

    @Test
    void stock_nullOrNegative_is400() {
        plainProduct(5);
        assertThatThrownBy(() -> service.updateProductStock(1L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateProductStock(1L, stock(null, null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateProductStock(1L, stock(-1, null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void stock_unknownProduct_is404() {
        assertThatThrownBy(() -> service.updateProductStock(1L, stock(1, null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void stock_boundaries_zeroAndMaxAccepted_maxPlusOneRefused() {
        Product p = plainProduct(5);
        service.updateProductStock(1L, stock(0, null));
        assertThat(p.getStockQuantity()).isZero();
        service.updateProductStock(1L, stock(100_000, null));
        assertThat(p.getStockQuantity()).isEqualTo(100_000);
        assertThatThrownBy(() -> service.updateProductStock(1L, stock(100_001, null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(p.getStockQuantity()).isEqualTo(100_000);
    }

    @Test
    void stock_noteLongerThan500_refused_exactly500_ok_andNoteIsCleaned() {
        Product p = plainProduct(5);
        assertThatThrownBy(() -> service.updateProductStock(1L, stock(6, "x".repeat(501)))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(p.getStockQuantity()).isEqualTo(5);
        service.updateProductStock(1L, stock(6, "x".repeat(500)));
        service.updateProductStock(1L, stock(7, "  recount   done "));
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledger, org.mockito.Mockito.atLeastOnce()).record(entries.capture(), any(), any(), any(), any());
        assertThat(entries.getValue().get(0).note()).isEqualTo("recount done");
    }

    @Test
    void stock_writesLedgerDelta_onlyWhenStockChanges() {
        plainProduct(5);
        service.updateProductStock(1L, stock(5, null));
        verify(ledger, never()).record(anyList(), any(), any(), any(), any());
        service.updateProductStock(1L, stock(8, null));
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledger).record(entries.capture(), any(), any(), any(), any());
        assertThat(entries.getValue()).hasSize(1);
        assertThat(entries.getValue().get(0).change()).isEqualTo(3);
        assertThat(entries.getValue().get(0).balanceAfter()).isEqualTo(8);
    }

    @Test
    void stock_decreaseIsANegativeDelta() {
        plainProduct(10);
        service.updateProductStock(1L, stock(4, null));
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledger).record(entries.capture(), any(), any(), any(), any());
        assertThat(entries.getValue().get(0).change()).isEqualTo(-6);
    }

    @Test
    void stock_productWithActiveColours_cannotBeSetByTotal() {
        Product p = plainProduct(10);
        ProductVariant v = new ProductVariant();
        v.setId(11L);
        v.setActive(true);
        v.setStockQuantity(10);
        p.getVariants().add(v);
        assertThatThrownBy(() -> service.updateProductStock(1L, stock(99, null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(p.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void stock_productWithOnlyInactiveColours_canBeSetByTotal() {
        Product p = plainProduct(10);
        ProductVariant v = new ProductVariant();
        v.setId(11L);
        v.setActive(false);
        p.getVariants().add(v);
        service.updateProductStock(1L, stock(3, null));
        assertThat(p.getStockQuantity()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ variant stock

    private Product withVariants(int... stocks) {
        Product p = Fx.product(1L, "Amy");
        long id = 11;
        for (int s : stocks) {
            ProductVariant v = new ProductVariant();
            v.setId(id++);
            v.setActive(true);
            v.setColorName("C" + id);
            v.setStockQuantity(s);
            p.getVariants().add(v);
        }
        when(products.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void variantStock_updatesColour_andRecomputesProductTotalFromActiveColoursOnly() {
        Product p = withVariants(5, 7, 100);
        ProductVariant third = p.getVariants().stream().filter(v -> v.getId() == 13L).findFirst().get();
        third.setActive(false);
        service.updateVariantStock(1L, 11L, stock(10, null));
        assertThat(p.getStockQuantity()).isEqualTo(17);   // 10 + 7, the inactive colour does not count
    }

    @Test
    void variantStock_validation_andUnknownColour() {
        withVariants(5);
        assertThatThrownBy(() -> service.updateVariantStock(1L, 11L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateVariantStock(1L, 11L, stock(-1, null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateVariantStock(1L, 11L, stock(100_001, null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateVariantStock(1L, 999L, stock(1, null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.updateVariantStock(2L, 11L, stock(1, null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void variantStock_aColourOfAnotherProduct_isNotFound() {
        Product p = withVariants(5);
        Product other = Fx.product(2L, "Other");
        ProductVariant foreign = new ProductVariant();
        foreign.setId(500L);
        foreign.setActive(true);
        other.getVariants().add(foreign);
        assertThatThrownBy(() -> service.updateVariantStock(1L, 500L, stock(1, null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        assertThat(p.getStockQuantity()).isEqualTo(0);
    }

    @Test
    void variantStock_soldOutToInStock_notifiesWaitingCustomers() {
        Product p = withVariants(0);
        service.updateVariantStock(1L, 11L, stock(4, null));
        verify(stockAlerts).notifyRestocked(p.getVariants().iterator().next());
    }

    @Test
    void variantStock_topUpOfColourAlreadyInStock_tellsNobody() {
        withVariants(3);
        service.updateVariantStock(1L, 11L, stock(9, null));
        verify(stockAlerts, never()).notifyRestocked(any());
    }

    @Test
    void variantStock_toZero_orInactiveColour_tellsNobody() {
        Product p = withVariants(5);
        service.updateVariantStock(1L, 11L, stock(0, null));
        verify(stockAlerts, never()).notifyRestocked(any());
        ProductVariant v = p.getVariants().iterator().next();
        v.setActive(false);
        v.setStockQuantity(0);
        service.updateVariantStock(1L, 11L, stock(5, null));
        verify(stockAlerts, never()).notifyRestocked(any());
    }

    @Test
    void variantStock_ledgerLineIsForTheColour() {
        withVariants(2);
        service.updateVariantStock(1L, 11L, stock(5, "recount"));
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledger).record(entries.capture(), any(), any(), any(), any());
        assertThat(entries.getValue().get(0).variant()).isNotNull();
        assertThat(entries.getValue().get(0).change()).isEqualTo(3);
    }
}
