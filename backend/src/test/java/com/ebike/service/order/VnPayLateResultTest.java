package com.ebike.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ebike.config.order.VnPayProperties;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.service.order.impl.VnPayServiceImpl;
import com.ebike.util.order.VnPayUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** B2: a VNPay result that arrives after the order was auto-cancelled must not bring the order back. */
@ExtendWith(MockitoExtension.class)
class VnPayLateResultTest {
    private static final String SECRET = "unit-test-secret";
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    VnPayServiceImpl service;
    Order order;
    Payment payment;

    @BeforeEach void setup() {
        VnPayProperties properties = new VnPayProperties();
        properties.setTmnCode("TEST");
        properties.setHashSecret(SECRET);
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:8080/payment/return");
        service = new VnPayServiceImpl(properties, orders, payments, new ObjectMapper());
        order = new Order();
        order.setId(9L);
        order.setOrderNumber("ORD-LATE");
        payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setAmount(new BigDecimal("20000000"));
        payment.setTransactionReference("TXN-LATE");
        order.setPayment(payment);
        when(payments.findByTransactionReference("TXN-LATE")).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Map<String, String> result(String code) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "TXN-LATE");
        params.put("vnp_Amount", "2000000000");
        params.put("vnp_ResponseCode", code);
        params.put("vnp_TransactionStatus", code);
        params.put("vnp_TransactionNo", "123456");
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(SECRET, VnPayUtil.buildQuery(params)));
        return params;
    }

    @Test void paymentAfterAutoCancelIsRecordedForRefundAndOrderStaysCancelled() {
        order.setStatus(OrderStatus.CANCELLED);
        payment.setPaymentStatus(PaymentStatus.CANCELLED);

        var response = service.handleReturnUrl(result("00"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus(), "a cancelled order must not come back to life");
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus(), "the money must be visible so the manager can refund it");
        assertTrue(response.success());
    }

    @Test void failedPaymentAfterAutoCancelLeavesEverythingCancelled() {
        order.setStatus(OrderStatus.CANCELLED);
        payment.setPaymentStatus(PaymentStatus.CANCELLED);

        service.handleIpnCallback(result("24"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());
    }

    @Test void onTimePaymentStillConfirmsTheOrder() {
        order.setStatus(OrderStatus.PENDING);
        payment.setPaymentStatus(PaymentStatus.PENDING);

        service.handleIpnCallback(result("00"));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
    }
}
