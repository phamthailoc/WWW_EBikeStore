package com.ebike.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.config.order.VnPayProperties;
import com.ebike.dto.manager.request.ManagerPaymentConfirmationRequest;
import com.ebike.dto.order.request.VnPayCreatePaymentRequest;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.manager.impl.ManagerServiceImpl;
import com.ebike.service.order.impl.VnPayServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** V24: the down payment of an instalment order: paid through VNPay or taken at the store, only after the application is approved, and only for the down payment. */
class InstallmentPaymentTest {

    private OrderRepository orders;
    private PaymentRepository payments;
    private InstallmentApplicationRepository installments;
    private VnPayServiceImpl vnpay;
    private ManagerServiceImpl manager;
    private Order order;
    private Payment payment;
    private InstallmentApplication application;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        payments = mock(PaymentRepository.class);
        installments = mock(InstallmentApplicationRepository.class);
        VnPayProperties properties = new VnPayProperties();
        properties.setTmnCode("TEST");
        properties.setHashSecret("unit-test-secret");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:8080/payment/return");
        vnpay = new VnPayServiceImpl(properties, orders, payments, installments, new ObjectMapper(), org.mockito.Mockito.mock(com.ebike.service.admin.AuditLogService.class), java.time.Duration.ofHours(72));
        manager = new ManagerServiceImpl(orders, payments, null, null, null, mock(AuditLogService.class), null, installments, java.time.Duration.ofHours(72), org.mockito.Mockito.mock(com.ebike.service.stock.StockLedger.class), org.mockito.Mockito.mock(OrderService.class));

        order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-10");
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("30000000"));
        payment = new Payment();
        payment.setId(7L);
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.INSTALLMENT);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("9000000"));
        order.setPayment(payment);
        application = new InstallmentApplication();
        application.setOrder(order);
        application.setStatus(InstallmentApplication.APPROVED);

        when(orders.findLockedById(10L)).thenReturn(Optional.of(order));
        when(payments.findOrderIdById(7L)).thenReturn(Optional.of(10L));
        when(payments.findByOrderId(10L)).thenReturn(Optional.of(payment));
        when(payments.findById(7L)).thenReturn(Optional.of(payment));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(installments.findByOrderId(10L)).thenReturn(Optional.of(application));
    }

    private VnPayCreatePaymentRequest pay() {
        return new VnPayCreatePaymentRequest(10L, null);
    }

    // ---- VNPay ----

    @Test
    void theGatewayChargesOnlyTheDownPaymentAndTheRowStaysAnInstalmentPayment() {
        var response = vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true);
        assertEquals(0, response.amount().compareTo(new BigDecimal("9000000")));
        assertTrue(response.paymentUrl().contains("vnp_Amount=900000000"));
        assertEquals(PaymentMethod.INSTALLMENT, payment.getPaymentMethod());
    }

    @Test
    void aFailedAttemptCanBeRetriedForTheSameDownPayment() {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true);
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(PaymentMethod.INSTALLMENT, payment.getPaymentMethod());
        assertEquals(0, payment.getAmount().compareTo(new BigDecimal("9000000")));
    }

    @Test
    void anApplicationNotApprovedYetCannotBePaid() {
        application.setStatus(InstallmentApplication.PENDING);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        application.setStatus(InstallmentApplication.REJECTED);
        assertThrows(ResponseStatusException.class, () -> vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true));
        verify(payments, never()).save(any());
    }

    @Test
    void aDownPaymentPastItsDeadlineCannotBePaidWhileTheOrderWaitsForTheExpiryJob() {
        application.setReviewedAt(java.time.LocalDateTime.now().minusHours(73));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(payments, never()).save(any());
        application.setReviewedAt(java.time.LocalDateTime.now().minusHours(71));
        vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true);
    }

    @Test
    void aPaymentPageIsNotOpenedWithMomentsOfTheHoldLeftAndNeverOutlastsIt() {
        application.setReviewedAt(java.time.LocalDateTime.now().minusHours(72).plusMinutes(1));
        assertThrows(ResponseStatusException.class, () -> vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true));
        verify(payments, never()).save(any());
        application.setReviewedAt(java.time.LocalDateTime.now().minusHours(72).plusMinutes(10));
        var response = vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true);
        assertTrue(response.expireTime().isBefore(java.time.LocalDateTime.now(com.ebike.util.order.VnPayUtil.ZONE).plusMinutes(11)));
    }

    @Test
    void anInstalmentPaymentWhoseApplicationRowIsMissingIsRefusedNotLetThrough() {
        when(installments.findByOrderId(10L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> vnpay.createPaymentUrl(pay(), "127.0.0.1", null, true));
        assertThrows(ResponseStatusException.class, () -> manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, null)));
        verify(payments, never()).save(any());
    }

    @Test
    void anOrderWithoutAnApplicationIsPaidInFullAsBefore() {
        Order plain = new Order();
        plain.setId(11L);
        plain.setOrderNumber("ORD-11");
        plain.setStatus(OrderStatus.PENDING);
        plain.setTotalAmount(new BigDecimal("20000000"));
        when(orders.findLockedById(11L)).thenReturn(Optional.of(plain));
        when(payments.findByOrderId(11L)).thenReturn(Optional.empty());
        when(installments.findByOrderId(11L)).thenReturn(Optional.empty());
        var response = vnpay.createPaymentUrl(new VnPayCreatePaymentRequest(11L, null), "127.0.0.1", null, true);
        assertEquals(0, response.amount().compareTo(new BigDecimal("20000000")));
    }

    // ---- at the store ----

    @Test
    void theStaffCanTakeTheDownPaymentAtTheStoreAndTheOrderMovesOn() {
        var response = manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, "Thu tiền mặt"));
        assertEquals("PAID", response.paymentStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void theStoreCannotTakeADownPaymentPastItsDeadline() {
        application.setReviewedAt(java.time.LocalDateTime.now().minusHours(73));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, null)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void aDownPaymentWhoseCardPaymentFailedCanStillBeTakenAtTheStore() {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setVnpayResponseCode("24");
        payment.setProviderResponse("{\"vnp_ResponseCode\":\"24\"}");
        var response = manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, "Thu tiền mặt"));
        assertEquals("PAID", response.paymentStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(null, payment.getVnpayResponseCode());
        assertEquals("Thu tiền mặt", payment.getProviderResponse());
    }

    @Test
    void theStoreWaitsWhileACardPaymentPageMayStillBeOpen() {
        payment.setTransactionReference("EBIKE" + com.ebike.util.order.VnPayUtil.formatDate(java.time.LocalDateTime.now(com.ebike.util.order.VnPayUtil.ZONE).minusMinutes(3)) + "ORD10ABCDEFGH");
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, null)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void anAbandonedCardPaymentPageDoesNotBlockTheStoreForLong() {
        payment.setTransactionReference("EBIKE" + com.ebike.util.order.VnPayUtil.formatDate(java.time.LocalDateTime.now(com.ebike.util.order.VnPayUtil.ZONE).minusMinutes(20)) + "ORD10ABCDEFGH");
        var response = manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, null));
        assertEquals("PAID", response.paymentStatus());
    }

    @Test
    void aFailedPayLaterPaymentStillCannotBeConfirmedByHand() {
        payment.setPaymentMethod(PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(PaymentStatus.FAILED);
        assertThrows(ResponseStatusException.class, () -> manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, null)));
    }

    @Test
    void theDownPaymentCannotBeTakenBeforeTheApplicationIsApproved() {
        application.setStatus(InstallmentApplication.PENDING);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void aVnpayPaymentIsStillNotConfirmedByHand() {
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        assertThrows(ResponseStatusException.class, () -> manager.confirmPayLaterPayment(7L, new ManagerPaymentConfirmationRequest(null, null)));
    }
}
