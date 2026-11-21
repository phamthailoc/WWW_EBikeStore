package com.ebike.service.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.ebike.common.constants.PermissionConstants;
import com.ebike.dto.installment.InstallmentApplicationResponse;
import com.ebike.dto.installment.InstallmentDecisionRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.order.OrderService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

/** V24: reading an application (who may) and the staff's verdict: approve opens the down payment, reject cancels the order. */
class InstallmentServiceTest {

    private InstallmentApplicationRepository applications;
    private UserRepository users;
    private OrderService orders;
    private OrderRepository orderRepository;
    private InstallmentService service;
    private User customer;
    private User staffUser;
    private Order order;
    private InstallmentApplication application;

    @BeforeEach
    void setUp() {
        applications = mock(InstallmentApplicationRepository.class);
        users = mock(UserRepository.class);
        orders = mock(OrderService.class);
        orderRepository = mock(OrderRepository.class);
        service = new InstallmentService(applications, users, orders, orderRepository, mock(AuditLogService.class), Duration.ofHours(72));

        customer = user(7L, "khach", "An", "Nguyen");
        staffUser = user(2L, "quanly", "Binh", "Tran");
        when(users.findByUsername("khach")).thenReturn(Optional.of(customer));
        when(users.findByUsername("quanly")).thenReturn(Optional.of(staffUser));
        when(users.findByUsername("khac")).thenReturn(Optional.of(user(8L, "khac", "Cuong", "Le")));

        Product product = new Product();
        product.setId(1L);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductName("KINETIC Test");
        item.setQuantity(1);
        order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-10");
        order.setStatus(OrderStatus.PENDING);
        order.getItems().add(item);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.INSTALLMENT);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("9000000"));
        order.setPayment(payment);

        application = new InstallmentApplication();
        application.setOrder(order);
        application.setUser(customer);
        application.setIdNumberMasked("********6789");
        application.setMonthlyIncome(new BigDecimal("20000000"));
        application.setOrderTotal(new BigDecimal("30000000"));
        application.setDownPaymentPercent(30);
        application.setDownPaymentAmount(new BigDecimal("9000000"));
        application.setFinancedAmount(new BigDecimal("21000000"));
        application.setTermMonths(12);
        application.setAnnualRatePercent(new BigDecimal("10"));
        application.setMonthlyPayment(new BigDecimal("1925000"));
        application.setTotalPayable(new BigDecimal("32100000"));
        when(applications.findLockedById(5L)).thenReturn(Optional.of(application));
        when(applications.findOrderIdById(5L)).thenReturn(Optional.of(10L));
        when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
        when(applications.findByOrderId(10L)).thenReturn(Optional.of(application));
        when(applications.saveAndFlush(any(InstallmentApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static User user(Long id, String username, String first, String last) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFirstName(first);
        user.setLastName(last);
        return user;
    }

    private static Authentication auth(String name, String... authorities) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        when(authentication.getAuthorities()).thenAnswer(invocation -> (Collection<? extends GrantedAuthority>) granted);
        return authentication;
    }

    private Authentication staff() {
        return auth("quanly", PermissionConstants.OrderManagement.ORDER_VIEW_ALL, PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS);
    }

    private static ResponseStatusException refused(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run);
    }

    // ---- reading ----

    @Test
    void theCustomerSeesTheirOwnApplicationWithoutTheReviewersName() {
        application.setReviewedBy(staffUser);
        InstallmentApplicationResponse response = service.forOrder(10L, auth("khach", PermissionConstants.Customer.ORDER_VIEW_OWN));
        assertEquals("ORD-10", response.orderNumber());
        assertEquals("KINETIC Test × 1", response.vehicles());
        assertNull(response.reviewedBy());
        assertEquals("********6789", response.idNumberMasked());
    }

    @Test
    void anotherCustomerGetsNotFoundNotForbidden() {
        ResponseStatusException error = refused(() -> service.forOrder(10L, auth("khac", PermissionConstants.Customer.ORDER_VIEW_OWN)));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void anOrderWithoutAnApplicationIsNotFound() {
        when(applications.findByOrderId(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, refused(() -> service.forOrder(99L, staff())).getStatusCode());
    }

    @Test
    void theStaffSeeTheReviewerAndMayDecideAWaitingApplication() {
        application.setReviewedBy(staffUser);
        InstallmentApplicationResponse response = service.forOrder(10L, staff());
        assertEquals("Binh Tran", response.reviewedBy());
        assertTrue(response.canDecide());
        assertFalse(response.canPay());
    }

    @Test
    void anApprovedApplicationLetsTheCustomerPayUntilTheDeadline() {
        LocalDateTime reviewed = LocalDateTime.of(2026, 11, 20, 9, 0);
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(reviewed);
        InstallmentApplicationResponse response = service.forOrder(10L, auth("khach", PermissionConstants.Customer.ORDER_VIEW_OWN));
        assertTrue(response.canPay());
        assertFalse(response.canDecide());
        assertEquals(reviewed.plusHours(72), response.payBefore());
        assertEquals("PENDING", response.paymentStatus());
    }

    @Test
    void onceTheDownPaymentIsPaidOrTheOrderIsCancelledThereIsNothingToPay() {
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(LocalDateTime.now());
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        assertFalse(service.forOrder(10L, staff()).canPay());
        order.getPayment().setPaymentStatus(PaymentStatus.PENDING);
        order.setStatus(OrderStatus.CANCELLED);
        InstallmentApplicationResponse cancelled = service.forOrder(10L, staff());
        assertFalse(cancelled.canPay());
        assertNull(cancelled.payBefore());
    }

    @Test
    void aDeletedCustomerShowsAPlaceholderAndNoPersonalData() {
        customer.setAccountStatus(AccountErasure.DELETED);
        customer.setFirstName("Tài khoản");
        customer.setLastName("đã xóa");
        application.setIdNumberMasked(null);
        application.setMonthlyIncome(null);
        InstallmentApplicationResponse response = service.forOrder(10L, staff());
        assertEquals("Tài khoản đã xóa", response.customerName());
        assertNull(response.idNumberMasked());
        assertNull(response.monthlyIncome());
    }

    // ---- the verdict ----

    @Test
    void approvingMarksTheApplicationAndLeavesTheOrderOpenForTheDownPayment() {
        InstallmentApplicationResponse response = service.decide(5L, new InstallmentDecisionRequest("approve", "  Mang theo CCCD  "), staff());
        assertEquals(InstallmentApplication.APPROVED, application.getStatus());
        assertEquals("Mang theo CCCD", application.getReviewNote());
        assertEquals(staffUser, application.getReviewedBy());
        assertNotNull(application.getReviewedAt());
        assertEquals("APPROVED", response.status());
        assertTrue(response.canPay());
        verify(orders, never()).updateOrderStatus(any(), any(), any());
    }

    @Test
    void theOrderRowIsLockedBeforeTheApplicationRow() {
        service.decide(5L, new InstallmentDecisionRequest("APPROVE", null), staff());
        var locks = org.mockito.Mockito.inOrder(orderRepository, applications);
        locks.verify(orderRepository).findLockedById(10L);
        locks.verify(applications).findLockedById(5L);
    }

    @Test
    void approvingNeedsNoNote() {
        service.decide(5L, new InstallmentDecisionRequest("APPROVE", null), staff());
        assertEquals(InstallmentApplication.APPROVED, application.getStatus());
        assertNull(application.getReviewNote());
    }

    @Test
    void rejectingCancelsTheOrderWithTheReasonAndKeepsTheVerdict() {
        Authentication staff = staff();
        service.decide(5L, new InstallmentDecisionRequest("REJECT", "Thu nhập chưa đủ"), staff);
        assertEquals(InstallmentApplication.REJECTED, application.getStatus());
        assertEquals("Thu nhập chưa đủ", application.getReviewNote());
        ArgumentCaptor<OrderStatusUpdateRequest> cancel = ArgumentCaptor.forClass(OrderStatusUpdateRequest.class);
        verify(orders).updateOrderStatus(eq(10L), cancel.capture(), eq(staff));
        assertEquals("CANCELLED", cancel.getValue().status());
        assertTrue(cancel.getValue().reason().contains("Thu nhập chưa đủ"));
    }

    @Test
    void rejectingNeedsAReason() {
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.decide(5L, new InstallmentDecisionRequest("REJECT", "   "), staff())).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.decide(5L, new InstallmentDecisionRequest("REJECT", null), staff())).getStatusCode());
        assertEquals(InstallmentApplication.PENDING, application.getStatus());
        verify(orders, never()).updateOrderStatus(any(), any(), any());
    }

    @Test
    void aRejectionNoteLeavesRoomForThePrefixOfTheCancellationReason() {
        String longest = "x".repeat(1000 - InstallmentService.REJECTED_REASON_PREFIX.length());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.decide(5L, new InstallmentDecisionRequest("REJECT", longest + "x"), staff())).getStatusCode());
        assertEquals(InstallmentApplication.PENDING, application.getStatus());
        service.decide(5L, new InstallmentDecisionRequest("REJECT", longest), staff());
        var reason = ArgumentCaptor.forClass(OrderStatusUpdateRequest.class);
        verify(orders).updateOrderStatus(eq(10L), reason.capture(), any());
        assertEquals(1000, reason.getValue().reason().length());
    }

    @Test
    void anUnknownDecisionOrALongNoteIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.decide(5L, new InstallmentDecisionRequest("MAYBE", "x"), staff())).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.decide(5L, new InstallmentDecisionRequest(null, "x"), staff())).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.decide(5L, null, staff())).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.decide(5L, new InstallmentDecisionRequest("APPROVE", "x".repeat(1001)), staff())).getStatusCode());
    }

    @Test
    void anApplicationAlreadyDecidedIsAConflictWhateverTheSecondDecision() {
        application.setStatus(InstallmentApplication.APPROVED);
        assertEquals(HttpStatus.CONFLICT, refused(() -> service.decide(5L, new InstallmentDecisionRequest("REJECT", "muộn"), staff())).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, refused(() -> service.decide(5L, new InstallmentDecisionRequest("APPROVE", null), staff())).getStatusCode());
        application.setStatus(InstallmentApplication.CANCELLED);
        assertEquals(HttpStatus.CONFLICT, refused(() -> service.decide(5L, new InstallmentDecisionRequest("APPROVE", null), staff())).getStatusCode());
        verify(orders, never()).updateOrderStatus(any(), any(), any());
    }

    @Test
    void anOrderThatMovedOnCannotBeDecided() {
        order.setStatus(OrderStatus.CONFIRMED);
        assertEquals(HttpStatus.CONFLICT, refused(() -> service.decide(5L, new InstallmentDecisionRequest("APPROVE", null), staff())).getStatusCode());
        assertEquals(InstallmentApplication.PENDING, application.getStatus());
    }

    @Test
    void anApplicationOfADeletedAccountCannotBeApproved() {
        customer.setAccountStatus(AccountErasure.DELETED);
        assertEquals(HttpStatus.CONFLICT, refused(() -> service.decide(5L, new InstallmentDecisionRequest("APPROVE", null), staff())).getStatusCode());
    }

    @Test
    void anUnknownApplicationIsNotFound() {
        when(applications.findLockedById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, refused(() -> service.decide(404L, new InstallmentDecisionRequest("APPROVE", null), staff())).getStatusCode());
    }

    // ---- the queue ----

    @Test
    void theQueueDefaultsToWaitingApplicationsOldestFirst() {
        when(applications.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any())).thenReturn(List.of(application));
        assertEquals(1, service.queue(null).size());
        assertEquals(1, service.queue("pending").size());
        verify(applications, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void aClosedStatusListsNewestFirstAndAllListsEverything() {
        when(applications.findByStatusOrderByCreatedAtDesc(eq("REJECTED"), any())).thenReturn(List.of());
        when(applications.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(application));
        assertEquals(0, service.queue("rejected").size());
        assertEquals(1, service.queue("ALL").size());
    }

    @Test
    void anUnknownQueueStatusIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> service.queue("DONE")).getStatusCode());
    }

    @Test
    void theBacklogCountsWaitingApplications() {
        when(applications.countByStatus("PENDING")).thenReturn(4L);
        assertEquals(4L, service.backlog().pending());
    }
}
