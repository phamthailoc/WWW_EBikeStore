package com.ebike.service.order;

import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.order.impl.OrderServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStatusUpdateTest {
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock PromotionRepository promotions;
    @Mock OrderEmailVerificationService verification;
    @Mock com.ebike.repository.admin.PricingRuleRepository pricingRules;
    @Mock Authentication staff;
    OrderServiceImpl service;
    Order order;
    Product product;

    @BeforeEach void setup() {
        service = new OrderServiceImpl(orders, users, products, showrooms, promotions, verification, new com.ebike.service.admin.PricingService(pricingRules), org.mockito.Mockito.mock(com.ebike.service.admin.AuditLogService.class));
        product = new Product();
        product.setId(1L);
        product.setStockQuantity(5);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.PENDING);
        order.getItems().add(item);
        org.mockito.Mockito.lenient().when(orders.findById(10L)).thenReturn(Optional.of(order));
    }

    private void saves() { when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0)); }
    private void staffMember() {
        User manager = new User();
        manager.setId(99L);
        when(staff.getName()).thenReturn("manager");
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));
    }
    private void move(String status) { service.updateOrderStatus(10L, new OrderStatusUpdateRequest(status), staff); }

    @Test void aVehicleOrderCannotLeaveTheStoreWithoutItsFrameAndEngineNumbers() {
        saves();
        com.ebike.model.entity.product.ProductSpecification specification = new com.ebike.model.entity.product.ProductSpecification();
        specification.setVehicleType(com.ebike.model.entity.product.VehicleType.E_MOTORBIKE);
        product.setSpecification(specification);
        order.setStatus(OrderStatus.PROCESSING);
        var ex = assertThrows(ResponseStatusException.class, () -> move("SHIPPED"));
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        // two bikes, two entries: now it goes through
        for (String frame : new String[] {"RLHABC12345", "RLHABC12346"}) {
            com.ebike.model.entity.order.OrderItemUnit unit = new com.ebike.model.entity.order.OrderItemUnit();
            unit.setFrameNumber(frame);
            unit.setEngineNumber("M1234567");
            order.getItems().iterator().next().getUnits().add(unit);
        }
        move("SHIPPED");
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        move("DELIVERED");
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test void anOrderShippedBeforeTheNumbersExistedCanStillBeDelivered() {
        saves();
        com.ebike.model.entity.product.ProductSpecification specification = new com.ebike.model.entity.product.ProductSpecification();
        specification.setVehicleType(com.ebike.model.entity.product.VehicleType.E_MOTORBIKE);
        product.setSpecification(specification);
        order.setStatus(OrderStatus.SHIPPED); // left the store before V18, so no numbers were recorded
        move("DELIVERED");
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test void staffFindAnOrderByTheFrameOrEngineNumberOfItsVehicle() {
        staffMember();
        org.mockito.Mockito.doReturn(java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
            com.ebike.common.constants.PermissionConstants.OrderManagement.ORDER_VIEW_ALL))).when(staff).getAuthorities();
        com.ebike.model.entity.order.OrderItemUnit unit = new com.ebike.model.entity.order.OrderItemUnit();
        unit.setFrameNumber("RLHABC12345");
        unit.setEngineNumber("MOTOR-998877");
        order.getItems().iterator().next().getUnits().add(unit);
        when(orders.findAll()).thenReturn(java.util.List.of(order));
        assertEquals(1, service.getOrders(staff, null, null, null, "abc123", null).size());
        assertEquals(1, service.getOrders(staff, null, null, null, "motor 998877", null).size());
        assertEquals(0, service.getOrders(staff, null, null, null, "khongco", null).size());
    }

    @Test void ordersMoveForwardOneStepAtATime() {
        saves();
        move("CONFIRMED");
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        move("PROCESSING");
        move("SHIPPED");
        move("DELIVERED");
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test void stepsThatSkipAheadOrGoBackAreRejected() {
        assertThrows(ResponseStatusException.class, () -> move("SHIPPED"));
        order.setStatus(OrderStatus.SHIPPED);
        assertThrows(ResponseStatusException.class, () -> move("PROCESSING"));
        assertThrows(ResponseStatusException.class, () -> move("CANCELLED"));
        verify(orders, never()).save(any());
    }

    @Test void cancelledAndDeliveredOrdersAreFinal() {
        order.setStatus(OrderStatus.CANCELLED);
        assertThrows(ResponseStatusException.class, () -> move("DELIVERED"));
        order.setStatus(OrderStatus.DELIVERED);
        assertThrows(ResponseStatusException.class, () -> move("CANCELLED"));
        verify(orders, never()).save(any());
    }

    @Test void cancellingNeedsAReason() {
        assertThrows(ResponseStatusException.class,
            () -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "  "), staff));
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(5, product.getStockQuantity());
    }

    @Test void cancellingAnOrderThatUsedACodeGivesTheUseBack() {
        saves();
        staffMember();
        var promotion = new com.ebike.model.entity.admin.Promotion();
        org.springframework.test.util.ReflectionTestUtils.setField(promotion, "id", 7L);
        order.setPromotion(promotion);
        move("CONFIRMED");
        verify(promotions, never()).releaseUsage(any());
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "Khách đổi ý"), staff);
        verify(promotions).releaseUsage(7L);
    }

    @Test void cancellingAnOrderWithoutACodeLeavesPromotionsAlone() {
        saves();
        staffMember();
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "Khách đổi ý"), staff);
        verify(promotions, never()).releaseUsage(any());
    }

    @Test void staffCancellationReturnsStockClosesPaymentAndKeepsTheReason() {
        saves();
        staffMember();
        order.setStatus(OrderStatus.PROCESSING);
        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        order.setPayment(payment);

        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "Hết xe trong kho"), staff);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("Hết xe trong kho", order.getCancellationReason());
        assertEquals(OrderStatus.PROCESSING, order.getCancellationRequestedFromStatus());
        assertEquals(99L, order.getCancellationRequestedBy().getId());
        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());
        assertEquals(7, product.getStockQuantity());
    }

    @Test void aPaidPaymentIsLeftForTheRefundStep() {
        saves();
        staffMember();
        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(PaymentStatus.PAID);
        order.setPayment(payment);

        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "Khách đổi ý"), staff);

        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
    }
}
