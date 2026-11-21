package com.ebike.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.installment.InstallmentRequest;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.admin.PricingService;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.order.impl.OrderServiceImpl;
import com.ebike.service.stock.StockAlertService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V24: an order paid by instalments: how it is placed, who may place it, what it holds and when it is let go. */
class InstallmentOrderTest {

    private OrderRepository orders;
    private UserRepository users;
    private ProductRepository products;
    private ShowroomRepository showrooms;
    private InstallmentApplicationRepository installments;
    private OrderServiceImpl service;
    private Product product;
    private User customer;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        users = mock(UserRepository.class);
        products = mock(ProductRepository.class);
        showrooms = mock(ShowroomRepository.class);
        installments = mock(InstallmentApplicationRepository.class);
        PricingService pricing = new PricingService(mock(PricingRuleRepository.class));
        InstallmentPlanner planner = new InstallmentPlanner(pricing, installments, users,
            new BigDecimal("5000000"), new BigDecimal("300000000"), new BigDecimal("3000000"), new BigDecimal("1000000000"), 60, 2);
        service = new OrderServiceImpl(orders, users, products, showrooms, mock(PromotionRepository.class),
            mock(OrderEmailVerificationService.class), pricing, mock(AuditLogService.class), mock(StockAlertService.class), planner, installments);

        product = new Product();
        product.setId(1L);
        product.setName("KINETIC Test");
        product.setPrice(new BigDecimal("30000000"));
        product.setStockQuantity(8);
        product.setActive(true);
        var spec = new com.ebike.model.entity.product.ProductSpecification();
        spec.setVehicleType(com.ebike.model.entity.product.VehicleType.E_SCOOTER);
        spec.setMaxSpeedKmh(new BigDecimal("20"));
        spec.setMotorPowerWatts(200); // a bike that needs no plate
        product.setSpecification(spec);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        Showroom showroom = new Showroom();
        showroom.setId(3L);
        showroom.setActive(true);
        when(showrooms.findById(3L)).thenReturn(Optional.of(showroom));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 55L);
            return saved;
        });
        when(installments.save(any(InstallmentApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        customer = new User();
        customer.setId(7L);
        customer.setUsername("khach");
        when(users.findByUsername("khach")).thenReturn(Optional.of(customer));
        when(users.findByUsername("quanly")).thenReturn(Optional.of(new User()));
    }

    private static Authentication auth(String name, String... authorities) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        when(authentication.getAuthorities()).thenAnswer(invocation -> (Collection<? extends GrantedAuthority>) granted);
        return authentication;
    }

    private static OrderCreateRequest request(String method, InstallmentRequest installment) {
        return new OrderCreateRequest(null, "Khách Hàng", "0901234567", "khach@example.com", null, 3L, null, method, null, null, null,
            false, null, List.of(new OrderCreateItemRequest(1L, 1)), "STORE_PICKUP", null, installment);
    }

    private static InstallmentRequest application() {
        return new InstallmentRequest("079123456789", new BigDecimal("20000000"), 30, 12);
    }

    private static ResponseStatusException refused(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run);
    }

    // ---- placing the order ----

    @Test
    void anInstalmentOrderHoldsTheDownPaymentAsItsPaymentAndRecordsTheApplication() {
        var response = service.createOrder(request("INSTALLMENT", application()), auth("khach", "ROLE_CUSTOMER"));
        assertEquals("INSTALLMENT", response.paymentMethod());
        assertEquals("PENDING", response.paymentStatus());
        assertEquals(0, response.totalAmount().compareTo(new BigDecimal("30000000")));
        var saved = org.mockito.ArgumentCaptor.forClass(InstallmentApplication.class);
        verify(installments).save(saved.capture());
        assertEquals(0, saved.getValue().getDownPaymentAmount().compareTo(new BigDecimal("9000000")));
        assertEquals("********6789", saved.getValue().getIdNumberMasked());
        assertEquals(55L, saved.getValue().getOrder().getId());
        // the stock is held from the moment the application is sent
        assertEquals(7, product.getStockQuantity());
        var order = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orders).save(order.capture());
        assertEquals(PaymentMethod.INSTALLMENT, order.getValue().getPayment().getPaymentMethod());
        assertEquals(0, order.getValue().getPayment().getAmount().compareTo(new BigDecimal("9000000")));
    }

    @Test
    void anOrderPaidAnotherWayHasNoApplication() {
        service.createOrder(request("PAY_LATER", null), auth("khach", "ROLE_CUSTOMER"));
        verify(installments, never()).save(any());
        // an application sent along with another method is ignored, not recorded
        service.createOrder(request("PAY_LATER", application()), auth("khach", "ROLE_CUSTOMER"));
        verify(installments, never()).save(any());
    }

    @Test
    void aVisitorCannotPayByInstalments() {
        assertEquals(HttpStatus.UNAUTHORIZED, refused(() -> service.createOrder(request("INSTALLMENT", application()), null)).getStatusCode());
        verify(orders, never()).save(any());
    }

    @Test
    void aStaffAccountCannotPayByInstalments() {
        Authentication manager = auth("quanly", "ROLE_MANAGER");
        assertEquals(HttpStatus.FORBIDDEN, refused(() -> service.createOrder(request("INSTALLMENT", application()), manager)).getStatusCode());
        verify(orders, never()).save(any());
    }

    @Test
    void aBadApplicationHoldsNoStockAndSavesNothing() {
        var noIncome = new InstallmentRequest("079123456789", null, 30, 12);
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.createOrder(request("INSTALLMENT", noIncome), auth("khach", "ROLE_CUSTOMER"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.createOrder(request("INSTALLMENT", null), auth("khach", "ROLE_CUSTOMER"))).getStatusCode());
        verify(orders, never()).save(any());
        verify(installments, never()).save(any());
    }

    // ---- confirming ----

    private Order pendingInstalmentOrder(String applicationStatus) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        Order order = new Order();
        order.setId(10L);
        order.setUser(customer);
        order.setStatus(OrderStatus.PENDING);
        order.getItems().add(item);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.INSTALLMENT);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("9000000"));
        order.setPayment(payment);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 11, 1, 9, 0));
        InstallmentApplication application = new InstallmentApplication();
        application.setOrder(order);
        application.setStatus(applicationStatus);
        when(installments.findByOrderId(10L)).thenReturn(Optional.of(application));
        when(orders.findLockedById(10L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void theStaffCannotConfirmAnInstalmentOrderBeforeItsApplicationIsApproved() {
        Order order = pendingInstalmentOrder(InstallmentApplication.PENDING);
        Authentication staff = auth("quanly", "ROLE_MANAGER");
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CONFIRMED"), staff)).getStatusCode());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void theStaffCannotConfirmAnApprovedInstalmentOrderBeforeTheDownPaymentIsIn() {
        Order order = pendingInstalmentOrder(InstallmentApplication.APPROVED);
        Authentication staff = auth("quanly", "ROLE_MANAGER");
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CONFIRMED"), staff)).getStatusCode());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void theStaffCanConfirmAnInstalmentOrderOnceItsApplicationIsApprovedAndTheDownPaymentIsPaid() {
        Order order = pendingInstalmentOrder(InstallmentApplication.APPROVED);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CONFIRMED"), auth("quanly", "ROLE_MANAGER"));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    // ---- cancelling and expiry ----

    @Test
    void cancellingAnOrderClosesItsOpenApplication() {
        Order order = pendingInstalmentOrder(InstallmentApplication.PENDING);
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.requestCancellation(10L, new com.ebike.dto.order.request.OrderCancellationRequest("Đổi ý", null), auth("khach", "ROLE_CUSTOMER"));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(installments).cancelOpenOfOrder(eq(10L), any(LocalDateTime.class));
        assertEquals(PaymentStatus.CANCELLED, order.getPayment().getPaymentStatus());
        assertEquals(9, product.getStockQuantity()); // the bike goes back on sale
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Test
    void anApplicationNobodyReviewedWithinSeventyTwoHoursReleasesItsOrder() {
        Order order = pendingInstalmentOrder(InstallmentApplication.PENDING);
        when(orders.findLockedByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(order));
        assertEquals(1, service.expireUnpaidOrders(NOW));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.getCancellationReason().contains("chưa duyệt hồ sơ trả góp"));
        verify(installments).cancelOpenOfOrder(eq(10L), any(LocalDateTime.class));
        assertEquals(9, product.getStockQuantity()); // the bike goes back on sale
    }

    @Test
    void aRecentApplicationKeepsItsOrder() {
        Order order = pendingInstalmentOrder(InstallmentApplication.PENDING);
        ReflectionTestUtils.setField(order, "createdAt", NOW.minusHours(10));
        when(orders.findLockedByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(order));
        assertEquals(0, service.expireUnpaidOrders(NOW));
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void anApprovedApplicationWaitsForTheDownPaymentFromTheDayItWasApprovedNotFromTheOrderDate() {
        Order order = pendingInstalmentOrder(InstallmentApplication.APPROVED);
        // the order is 19 days old but was approved yesterday: the down payment still has time
        InstallmentApplication application = installments.findByOrderId(10L).orElseThrow();
        application.setReviewedAt(NOW.minusHours(24));
        when(orders.findLockedByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(order));
        assertEquals(0, service.expireUnpaidOrders(NOW));
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertNull(order.getCancellationReason());
    }

    @Test
    void anApprovedApplicationWhoseDownPaymentIsNotPaidInTimeReleasesItsOrder() {
        Order order = pendingInstalmentOrder(InstallmentApplication.APPROVED);
        installments.findByOrderId(10L).orElseThrow().setReviewedAt(NOW.minusHours(73));
        when(orders.findLockedByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(order));
        assertEquals(1, service.expireUnpaidOrders(NOW));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertNotNull(order.getCancellationReason());
        assertTrue(order.getCancellationReason().contains("khoản trả trước"));
    }

    @Test
    void anInstalmentOrderWhoseDownPaymentIsPaidIsNeverExpired() {
        Order order = pendingInstalmentOrder(InstallmentApplication.APPROVED);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        when(orders.findLockedByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(order));
        assertEquals(0, service.expireUnpaidOrders(NOW));
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }
}
