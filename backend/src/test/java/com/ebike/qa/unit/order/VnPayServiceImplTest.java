package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.config.order.VnPayProperties;
import com.ebike.dto.order.request.VnPayCreatePaymentRequest;
import com.ebike.dto.order.response.VnPayCreatePaymentResponse;
import com.ebike.dto.order.response.VnPayReturnResponse;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.service.order.impl.VnPayServiceImpl;
import com.ebike.util.order.VnPayUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VnPayServiceImplTest {

    private static final String SECRET = "TEST-SECRET-KEY-0123456789";
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String RETURN_URL = "http://localhost:8080/payment/vnpay-return";

    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock InstallmentApplicationRepository installments;
    @Mock com.ebike.service.admin.AuditLogService audit;

    VnPayProperties properties;
    VnPayServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new VnPayProperties();
        properties.setTmnCode("TMN12345");
        properties.setHashSecret(SECRET);
        properties.setPayUrl(PAY_URL);
        properties.setReturnUrl(RETURN_URL);
        service = new VnPayServiceImpl(properties, orderRepository, paymentRepository, installments, new ObjectMapper(), audit, Duration.ofHours(72));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(77L);
            }
            return payment;
        });
    }

    // ---------------------------------------------------------------------------------------------
    // independent signing helpers (do not use the production signer to build the expected hashes)
    // ---------------------------------------------------------------------------------------------

    private static String sign(Map<String, String> params, String secret) {
        try {
            StringBuilder data = new StringBuilder();
            for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
                if (e.getValue() == null || e.getValue().isBlank() || e.getKey().equals("vnp_SecureHash") || e.getKey().equals("vnp_SecureHashType")) {
                    continue;
                }
                if (data.length() > 0) {
                    data.append('&');
                }
                data.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> gatewayParams(String txnRef, String amountInDong, String responseCode, String txnStatus, String txnNo) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "TMN12345");
        params.put("vnp_Amount", amountInDong);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don hang");
        params.put("vnp_PayDate", "20261126101500");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", sign(params, SECRET));
        return params;
    }

    private static Map<String, String> success(String txnRef, String amountInDong, String txnNo) {
        return gatewayParams(txnRef, amountInDong, "00", "00", txnNo);
    }

    private Order stubPendingPayment(OrderStatus orderStatus, PaymentMethod method, PaymentStatus paymentStatus, String amount, String txnRef) {
        Order order = OrderFixtures.order(10L, "ORD-20261126-ABCD1234", orderStatus, OrderFixtures.user(1L, "alice", "a@x.com"));
        Payment payment = OrderFixtures.payment(order, method, paymentStatus, amount);
        payment.setTransactionReference(txnRef);
        when(paymentRepository.findOrderIdByTransactionReference(txnRef)).thenReturn(Optional.of(10L));
        when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByTransactionReference(txnRef)).thenReturn(Optional.of(payment));
        return order;
    }

    // ---------------------------------------------------------------------------------------------
    // createPaymentUrl
    // ---------------------------------------------------------------------------------------------

    private Order ownedOrder(String total) {
        Order order = OrderFixtures.order(10L, "ORD-20261126-ABCD1234", OrderStatus.PENDING, OrderFixtures.user(1L, "alice", "a@x.com"));
        order.setTotalAmount(new BigDecimal(total));
        when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(installments.findByOrderId(10L)).thenReturn(Optional.empty());
        return order;
    }

    private static Map<String, String> parse(String url) {
        String query = url.substring(url.indexOf('?') + 1);
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    @Test
    void create_refusesWhenVnPayIsNotConfigured() {
        properties.setHashSecret(" ");
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        verify(orderRepository, never()).findLockedById(any());
    }

    @Test
    void create_nullRequestOrOrderIdIs400() {
        assertThatThrownBy(() -> service.createPaymentUrl(null, "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(null, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_unknownOrderIs404() {
        when(orderRepository.findLockedById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(99L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_anotherCustomerCannotPayMyOrder() {
        ownedOrder("1000000");
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "mallory", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void create_anonymousCallerCannotPayAnAccountOrder() {
        ownedOrder("1000000");
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", null, false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void create_ownerBackOfficeAndGuestOrderAreAllowed() {
        Order order = ownedOrder("1000000");
        assertThat(service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false).paymentUrl()).isNotBlank();
        assertThat(service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "staff-user", true).paymentUrl()).isNotBlank();
        order.setUser(null); // a guest order is paid from the checkout page
        assertThat(service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", null, false).paymentUrl()).isNotBlank();
    }

    @Test
    void create_cancelledOrderCannotBePaid() {
        Order order = ownedOrder("1000000");
        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_buildsASignedUrlWithTheAmountTimes100() {
        ownedOrder("25990000");
        LocalDateTime before = LocalDateTime.now(VnPayUtil.ZONE);

        VnPayCreatePaymentResponse response = service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, " NCB "), "203.0.113.9", "alice", false);

        assertThat(response.paymentUrl()).startsWith(PAY_URL + "?");
        Map<String, String> params = parse(response.paymentUrl());
        assertThat(params).containsEntry("vnp_Version", "2.1.0").containsEntry("vnp_Command", "pay")
            .containsEntry("vnp_TmnCode", "TMN12345").containsEntry("vnp_CurrCode", "VND").containsEntry("vnp_Locale", "vn")
            .containsEntry("vnp_OrderType", "other").containsEntry("vnp_ReturnUrl", RETURN_URL)
            .containsEntry("vnp_IpAddr", "203.0.113.9").containsEntry("vnp_BankCode", "NCB")
            .containsEntry("vnp_Amount", "2599000000").containsEntry("vnp_TxnRef", response.txnRef());
        assertThat(params.get("vnp_OrderInfo")).contains("ORD-20261126-ABCD1234");
        assertThat(params).doesNotContainKey("vnp_SecureHashType");
        // the hash is HMAC-SHA512 over the sorted, encoded query without the hash itself
        assertThat(params.get("vnp_SecureHash")).isEqualTo(sign(params, SECRET));
        assertThat(response.amount()).isEqualByComparingTo("25990000");
        assertThat(response.orderNumber()).isEqualTo("ORD-20261126-ABCD1234");
        assertThat(response.paymentId()).isEqualTo(77L);
        // 15 minute payment page
        LocalDateTime created = LocalDateTime.parse(params.get("vnp_CreateDate"), java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        LocalDateTime expires = LocalDateTime.parse(params.get("vnp_ExpireDate"), java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        assertThat(Duration.between(created, expires)).isEqualTo(Duration.ofMinutes(15));
        assertThat(created).isBetween(before.minusSeconds(2), LocalDateTime.now(VnPayUtil.ZONE).plusSeconds(2));
        assertThat(response.expireTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)).isEqualTo(expires);
    }

    @Test
    void create_aTamperedUrlNoLongerMatchesItsHash() {
        ownedOrder("1000000");
        Map<String, String> params = parse(service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false).paymentUrl());
        params.put("vnp_Amount", "100");
        assertThat(params.get("vnp_SecureHash")).isNotEqualTo(sign(params, SECRET));
    }

    @Test
    void create_blankBankCodeIsLeftOut() {
        ownedOrder("1000000");
        Map<String, String> params = parse(service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, "  "), "1.2.3.4", "alice", false).paymentUrl());
        assertThat(params).doesNotContainKey("vnp_BankCode");
    }

    @Test
    void create_transactionReferenceIsReadableByTheExpiryCheckAndUnique() {
        ownedOrder("1000000");
        String first = service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false).txnRef();
        assertThat(first).startsWith("EBIKE").hasSizeLessThanOrEqualTo(100).matches("[A-Za-z0-9]+");
        assertThat(VnPayUtil.attemptStartedAt(first)).isNotNull();
        assertThat(VnPayUtil.attemptMayBeOpen(first, LocalDateTime.now(VnPayUtil.ZONE))).isTrue();
        assertThat(first).contains("ORD20261126ABCD1234");
    }

    @Test
    void create_aPendingOnlinePaymentGetsANewReferenceAndTheCurrentTotal() {
        Order order = ownedOrder("2000000");
        Payment existing = OrderFixtures.payment(order, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000");
        existing.setTransactionReference("EBIKE20260101000000OLD");
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(existing));

        VnPayCreatePaymentResponse response = service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false);

        assertThat(response.txnRef()).isNotEqualTo("EBIKE20260101000000OLD");
        assertThat(existing.getAmount()).isEqualByComparingTo("2000000");
        assertThat(parse(response.paymentUrl()).get("vnp_Amount")).isEqualTo("200000000");
    }

    @Test
    void create_aFailedPaymentIsResetForARetry() {
        Order order = ownedOrder("1000000");
        Payment failed = OrderFixtures.payment(order, PaymentMethod.VNPAY, PaymentStatus.FAILED, "1000000");
        failed.setTransactionReference("EBIKE20260101000000OLD");
        failed.setProviderTxnId("999");
        failed.setVnpayResponseCode("24");
        failed.setPaidAt(LocalDateTime.now());
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(failed));

        service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false);

        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(failed.getProviderTxnId()).isNull();
        assertThat(failed.getVnpayResponseCode()).isNull();
        assertThat(failed.getPaidAt()).isNull();
        assertThat(failed.getTransactionReference()).isNotEqualTo("EBIKE20260101000000OLD");
    }

    @Test
    void create_aPayLaterOrderSwitchesToVnPay() {
        Order order = ownedOrder("1000000");
        Payment later = OrderFixtures.payment(order, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "1000000");
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(later));

        service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false);

        assertThat(later.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(later.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void create_anAlreadyPaidOrderCannotBePaidTwice() {
        Order order = ownedOrder("1000000");
        Payment paid = OrderFixtures.payment(order, PaymentMethod.VNPAY, PaymentStatus.PAID, "1000000");
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(paid));
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void regression_zeroTotalOrderGetsAVnPayUrl() {
        // BUG (Low): VnPayServiceImpl.java:124 builds a payment page for an order whose total is 0 (a 100% fixed promotion on a pickup order
        // gives total 0). VNPay refuses vnp_Amount=0, so the customer is sent to an error page and the order is left to expire.
        // Expected: 400 "nothing to pay". Actual: a signed URL with vnp_Amount=0.
        ownedOrder("0");
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- instalment down payments --------------------------------------------------------------------

    private InstallmentApplication application(String status, LocalDateTime reviewedAt) {
        InstallmentApplication application = new InstallmentApplication();
        application.setStatus(status);
        application.setReviewedAt(reviewedAt);
        when(installments.findByOrderId(10L)).thenReturn(Optional.of(application));
        return application;
    }

    private Payment installmentPayment(Order order) {
        Payment down = OrderFixtures.payment(order, PaymentMethod.INSTALLMENT, PaymentStatus.PENDING, "5000000");
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(down));
        return down;
    }

    @Test
    void create_installmentDownPaymentChargesOnlyTheDownPayment() {
        Order order = ownedOrder("25000000");
        Payment down = installmentPayment(order);
        application(InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(1));

        VnPayCreatePaymentResponse response = service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false);

        assertThat(parse(response.paymentUrl()).get("vnp_Amount")).isEqualTo("500000000");
        assertThat(parse(response.paymentUrl()).get("vnp_OrderInfo")).startsWith("Tra truoc tra gop don");
        assertThat(down.getPaymentMethod()).isEqualTo(PaymentMethod.INSTALLMENT);
        assertThat(down.getAmount()).isEqualByComparingTo("5000000");
    }

    @Test
    void create_installmentNotYetApprovedIsRefused() {
        Order order = ownedOrder("25000000");
        installmentPayment(order);
        application("PENDING", null);
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_installmentPaymentWithoutAnApplicationRowIsRefused() {
        Order order = ownedOrder("25000000");
        installmentPayment(order);
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_installmentPastTheDownPaymentHoldIsRefused() {
        Order order = ownedOrder("25000000");
        installmentPayment(order);
        application(InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(73));
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_installmentWithLessThanTwoMinutesOfHoldLeftIsRefused() {
        Order order = ownedOrder("25000000");
        installmentPayment(order);
        application(InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(72).plusSeconds(90));
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_installmentPageNeverOutlastsTheHold() {
        Order order = ownedOrder("25000000");
        installmentPayment(order);
        // 10 minutes of the hold left: the page closes with the hold, not after the usual 15 minutes
        application(InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(72).plusMinutes(10));

        Map<String, String> params = parse(service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false).paymentUrl());

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        Duration open = Duration.between(LocalDateTime.parse(params.get("vnp_CreateDate"), f), LocalDateTime.parse(params.get("vnp_ExpireDate"), f));
        assertThat(open).isLessThanOrEqualTo(Duration.ofMinutes(10)).isGreaterThan(Duration.ofMinutes(9));
    }

    @Test
    void create_aPaidInstallmentDownPaymentSaysSo() {
        Order order = ownedOrder("25000000");
        Payment down = OrderFixtures.payment(order, PaymentMethod.INSTALLMENT, PaymentStatus.PAID, "5000000");
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(down));
        application(InstallmentApplication.APPROVED, LocalDateTime.now().minusHours(1));
        assertThatThrownBy(() -> service.createPaymentUrl(new VnPayCreatePaymentRequest(10L, null), "1.2.3.4", "alice", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).contains("trả trước"));
    }

    // ---------------------------------------------------------------------------------------------
    // IPN
    // ---------------------------------------------------------------------------------------------

    @Test
    void ipn_badSignatureIs97AndTouchesNothing() {
        Map<String, String> params = success("EBIKE1", "100000", "1");
        params.put("vnp_SecureHash", "deadbeef");
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "97");
        verify(paymentRepository, never()).save(any());
        verify(paymentRepository, never()).findByTransactionReference(any());
    }

    @Test
    void ipn_missingOrBlankSignatureIs97() {
        Map<String, String> params = success("EBIKE1", "100000", "1");
        params.remove("vnp_SecureHash");
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "97");
        params.put("vnp_SecureHash", "  ");
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "97");
    }

    @Test
    void ipn_signedWithAnotherSecretIs97() {
        Map<String, String> params = gatewayParams("EBIKE1", "100000", "00", "00", "1");
        params.put("vnp_SecureHash", sign(params, "ANOTHER-SECRET"));
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "97");
    }

    @Test
    void ipn_changingTheAmountAfterSigningIs97() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "5001");
        params.put("vnp_Amount", "100");
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "97");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void ipn_anExtraUnsignedParameterBreaksTheSignature() {
        stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "5001");
        params.put("evil", "1");
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "97");
    }

    @Test
    void ipn_signatureComparisonIgnoresCase() {
        stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "5001");
        params.put("vnp_SecureHash", params.get("vnp_SecureHash").toUpperCase());
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "00");
    }

    @Test
    void ipn_secureHashTypeIsNotPartOfTheSignature() {
        stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "5001");
        params.put("vnp_SecureHashType", "HmacSHA512");
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "00").containsEntry("Message", "Confirm Success");
    }

    @Test
    void ipn_unknownReferenceIs01() {
        when(paymentRepository.findOrderIdByTransactionReference("EBIKE-UNKNOWN")).thenReturn(Optional.empty());
        assertThat(service.handleIpnCallback(success("EBIKE-UNKNOWN", "100000", "1"))).containsEntry("RspCode", "01");
    }

    @Test
    void ipn_missingReferenceIs01() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "100");
        params.put("vnp_SecureHash", sign(params, SECRET));
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "01");
    }

    @Test
    void ipn_amountMismatchIs04AndNothingIsConfirmed() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> reply = service.handleIpnCallback(success("EBIKE20261126101000X", "50000000", "5001")); // 500,000 paid for 1,000,000
        assertThat(reply).containsEntry("RspCode", "04");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void ipn_overpaymentIsAlsoAMismatch() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        assertThat(service.handleIpnCallback(success("EBIKE20261126101000X", "200000000", "5001"))).containsEntry("RspCode", "04");
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void ipn_nonNumericOrFractionalAmountIs04() {
        stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        assertThat(service.handleIpnCallback(success("EBIKE20261126101000X", "abc", "5001"))).containsEntry("RspCode", "04");
        assertThat(service.handleIpnCallback(success("EBIKE20261126101000X", "100000000.5", "5001"))).containsEntry("RspCode", "04");
        assertThat(service.handleIpnCallback(success("EBIKE20261126101000X", "-100000000", "5001"))).containsEntry("RspCode", "04");
    }

    @Test
    void ipn_successConfirmsThePaymentAndTheOrder() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        LocalDateTime before = LocalDateTime.now();

        Map<String, String> reply = service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "14012345"));

        assertThat(reply).containsEntry("RspCode", "00").containsEntry("Message", "Confirm Success");
        Payment payment = order.getPayment();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getProviderTxnId()).isEqualTo("14012345");
        assertThat(payment.getVnpayResponseCode()).isEqualTo("00");
        assertThat(payment.getVnpayBankCode()).isEqualTo("NCB");
        assertThat(payment.getPaidAt()).isBetween(before.minusSeconds(1), LocalDateTime.now().plusSeconds(1));
        assertThat(payment.getProviderResponse()).contains("14012345");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void ipn_lockTheOrderBeforeReadingThePayment() {
        stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "1"));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(orderRepository, paymentRepository);
        inOrder.verify(paymentRepository).findOrderIdByTransactionReference("EBIKE20261126101000X");
        inOrder.verify(orderRepository).findLockedById(10L);
        inOrder.verify(paymentRepository).findByTransactionReference("EBIKE20261126101000X");
    }

    @Test
    void ipn_failedCardMarksPaymentFailedAndLeavesTheOrderPending() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> reply = service.handleIpnCallback(gatewayParams("EBIKE20261126101000X", "100000000", "24", "02", "0"));
        assertThat(reply).containsEntry("RspCode", "00").containsEntry("Message", "Confirm Failed");
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getPayment().getPaidAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void ipn_responseCode00WithATransactionStatusThatIsNot00IsNotASuccess() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        service.handleIpnCallback(gatewayParams("EBIKE20261126101000X", "100000000", "00", "02", "9"));
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void ipn_aSuccessfulTransactionStatusWithAFailureResponseCodeIsNotASuccess() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        service.handleIpnCallback(gatewayParams("EBIKE20261126101000X", "100000000", "51", "00", "9"));
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void ipn_theSameSuccessTwiceIsIdempotent() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "777");
        service.handleIpnCallback(params);
        LocalDateTime firstPaidAt = order.getPayment().getPaidAt();

        Map<String, String> second = service.handleIpnCallback(params);

        assertThat(second).containsEntry("RspCode", "00").containsEntry("Message", "Order already confirmed");
        assertThat(order.getPayment().getPaidAt()).isEqualTo(firstPaidAt);
        assertThat(order.getPayment().getProviderResponse()).doesNotContain("DUPLICATE");
        verify(paymentRepository, times(1)).save(any(Payment.class)); // the retry wrote nothing
    }

    @Test
    void ipn_aFailureArrivingAfterASuccessDoesNotUndoThePayment() {
        Order order = stubPendingPayment(OrderStatus.CONFIRMED, PaymentMethod.VNPAY, PaymentStatus.PAID, "1000000", "EBIKE20261126101000X");
        order.getPayment().setProviderTxnId("777");
        Map<String, String> reply = service.handleIpnCallback(gatewayParams("EBIKE20261126101000X", "100000000", "24", "02", "0"));
        assertThat(reply).containsEntry("RspCode", "00");
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void ipn_aSecondDifferentSuccessfulTransactionIsFlaggedForRefundExactlyOnce() {
        Order order = stubPendingPayment(OrderStatus.CONFIRMED, PaymentMethod.VNPAY, PaymentStatus.PAID, "1000000", "EBIKE20261126101000X");
        order.getPayment().setProviderTxnId("111");
        Map<String, String> secondTransaction = success("EBIKE20261126101000X", "100000000", "222");

        service.handleIpnCallback(secondTransaction);
        service.handleIpnCallback(secondTransaction);

        String response = order.getPayment().getProviderResponse();
        assertThat(response).contains("DUPLICATE VNPAY PAYMENT 222");
        assertThat(response.split("DUPLICATE VNPAY PAYMENT 222", -1)).hasSize(2); // exactly one marker
        assertThat(order.getPayment().getProviderTxnId()).isEqualTo("111"); // the first payment is not overwritten
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void ipn_theRetryOfTheSameTransactionIsNotADuplicate() {
        Order order = stubPendingPayment(OrderStatus.CONFIRMED, PaymentMethod.VNPAY, PaymentStatus.PAID, "1000000", "EBIKE20261126101000X");
        order.getPayment().setProviderTxnId("111");
        service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "111"));
        assertThat(order.getPayment().getProviderResponse()).isNull();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void ipn_aDuplicateWithAWrongAmountIsNotRecordedAsAPayment() {
        Order order = stubPendingPayment(OrderStatus.CONFIRMED, PaymentMethod.VNPAY, PaymentStatus.PAID, "1000000", "EBIKE20261126101000X");
        order.getPayment().setProviderTxnId("111");
        service.handleIpnCallback(success("EBIKE20261126101000X", "100", "222"));
        assertThat(order.getPayment().getProviderResponse()).isNull();
    }

    // --- the order was cancelled while the customer was on the gateway page -------------------------

    @Test
    void ipn_lateSuccessForACancelledOrderRecordsThePaymentButKeepsTheOrderCancelled() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");

        Map<String, String> reply = service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "4242"));

        assertThat(reply).containsEntry("RspCode", "00").containsEntry("Message", "Confirm Success");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED); // its stock is already back on sale
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID); // the money shows up so the manager refunds it
        assertThat(order.getPayment().getProviderTxnId()).isEqualTo("4242");
        assertThat(order.getPayment().getPaidAt()).isNotNull();
        verify(paymentRepository).save(order.getPayment());
    }

    @Test
    void ipn_lateFailureForACancelledOrderStaysCancelled() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
        service.handleIpnCallback(gatewayParams("EBIKE20261126101000X", "100000000", "24", "02", "0"));
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    /** M-21: the money was taken for a cancelled order: VNPay is still answered 00, the order stays CANCELLED (the service has no stock collaborator at all, so stock cannot be re-reserved from here), the case is audited once, and a retry adds nothing. */
    @Test
    void ipn_lateSuccessForACancelledOrder_isAuditedOnceAndKeepsTheOrderCancelled() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "4242");

        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "00");
        assertThat(service.handleIpnCallback(params)).containsEntry("RspCode", "00");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(audit, times(1)).recordBy(org.mockito.ArgumentMatchers.eq("VNPay"), org.mockito.ArgumentMatchers.contains("cần hoàn tiền"), org.mockito.ArgumentMatchers.eq("ORD-20261126-ABCD1234"));
        verify(orderRepository, never()).save(any()); // the order row is not written at all
    }

    /** A failing audit write never turns a recorded payment into an error for the gateway (it would retry forever). */
    @Test
    void ipn_lateSuccess_auditFailureStillRecordsThePaymentAndAnswers00() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
        org.mockito.Mockito.doThrow(new IllegalStateException("audit down")).when(audit).recordBy(any(), any(), any());

        assertThat(service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "4242"))).containsEntry("RspCode", "00");

        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(order.getPayment());
    }

    /** Inside a transaction the audit entry waits for the commit: nothing is written if the transaction rolls back. */
    @Test
    void ipn_lateSuccess_auditIsWrittenOnlyAfterCommit() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
            service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "4242"));
            verify(audit, never()).recordBy(any(), any(), any());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(
                org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(audit, times(1)).recordBy(any(), any(), any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void return_lateSuccessForACancelledOrderWritesTheAuditOnce() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "4242");

        service.handleReturnUrl(params);
        service.handleReturnUrl(params);
        service.handleIpnCallback(params);

        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(audit, times(1)).recordBy(any(), any(), any());
    }

    /** A replayed IPN after the manager refunded the payment changes nothing and is still answered 00. */
    @Test
    void ipn_replayAfterAManagerRefundStaysRefunded() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.REFUNDED, "1000000", "EBIKE20261126101000X");
        order.getPayment().setProviderTxnId("4242");

        assertThat(service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "4242"))).containsEntry("RspCode", "00");

        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(audit, never()).recordBy(any(), any(), any());
    }

    @Test
    void ipn_lateSuccessForACancelledInstallmentDownPaymentIsRecordedToo() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.INSTALLMENT, PaymentStatus.CANCELLED, "5000000", "EBIKE20261126101000X");
        service.handleIpnCallback(success("EBIKE20261126101000X", "500000000", "4242"));
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void ipn_lateSuccessWithTheWrongAmountForACancelledOrderIsRejected() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
        assertThat(service.handleIpnCallback(success("EBIKE20261126101000X", "100", "4242"))).containsEntry("RspCode", "04");
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void ipn_aCancelledPayLaterPaymentNeverWentThroughTheGatewayAndIsNotConfirmed() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.PAY_LATER, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
        Map<String, String> reply = service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "4242"));
        assertThat(reply).containsEntry("Message", "Order already confirmed");
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void ipn_instalmentDownPaymentConfirmsTheOrder() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.INSTALLMENT, PaymentStatus.PENDING, "5000000", "EBIKE20261126101000X");
        service.handleIpnCallback(success("EBIKE20261126101000X", "500000000", "88"));
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void regression_ipnSuccessOnAnOrderAlreadyBeingProcessedMovesItBackToConfirmed() {
        // BUG (Medium): VnPayServiceImpl.java:324 applyGatewayResult sets the order to CONFIRMED on every successful payment, whatever
        // the order's current status. createPaymentUrl accepts any non-cancelled order, so a customer who pays online after the staff
        // already moved a pay-later order to PROCESSING (or READY_FOR_PICKUP / SHIPPED) drags the order back to CONFIRMED.
        // Expected: the status stays PROCESSING (only a PENDING order becomes CONFIRMED). Actual: CONFIRMED.
        Order order = stubPendingPayment(OrderStatus.PROCESSING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        service.handleIpnCallback(success("EBIKE20261126101000X", "100000000", "4242"));
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    // ---------------------------------------------------------------------------------------------
    // return URL
    // ---------------------------------------------------------------------------------------------

    @Test
    void return_badSignatureIsInvalidAndNeverSuccess() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "1");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "100"); // forged after signing

        VnPayReturnResponse response = service.handleReturnUrl(params);

        assertThat(response.valid()).isFalse();
        assertThat(response.success()).isFalse();
        assertThat(response.paymentId()).isNull();
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void return_aFreshSuccessIsAppliedWhenTheIpnHasNotArrivedYet() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");

        VnPayReturnResponse response = service.handleReturnUrl(success("EBIKE20261126101000X", "100000000", "4242"));

        assertThat(response.valid()).isTrue();
        assertThat(response.success()).isTrue();
        assertThat(response.amount()).isEqualByComparingTo("1000000");
        assertThat(response.orderNumber()).isEqualTo("ORD-20261126-ABCD1234");
        assertThat(response.orderId()).isEqualTo(10L);
        assertThat(response.paymentStatus()).isEqualTo("PAID");
        assertThat(response.paymentMethod()).isEqualTo("VNPAY");
        assertThat(response.transactionNo()).isEqualTo("4242");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void return_afterTheIpnItChangesNothingAgain() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        Map<String, String> params = success("EBIKE20261126101000X", "100000000", "4242");
        service.handleIpnCallback(params);
        LocalDateTime paidAt = order.getPayment().getPaidAt();

        VnPayReturnResponse response = service.handleReturnUrl(params);

        assertThat(response.success()).isTrue();
        assertThat(order.getPayment().getPaidAt()).isEqualTo(paidAt);
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void return_failedPaymentIsNotASuccess() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        VnPayReturnResponse response = service.handleReturnUrl(gatewayParams("EBIKE20261126101000X", "100000000", "24", "02", "0"));
        assertThat(response.valid()).isTrue();
        assertThat(response.success()).isFalse();
        assertThat(response.responseCode()).isEqualTo("24");
        assertThat(response.paymentStatus()).isEqualTo("FAILED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void return_amountMismatchIsNotAppliedAndNotASuccess() {
        Order order = stubPendingPayment(OrderStatus.PENDING, PaymentMethod.VNPAY, PaymentStatus.PENDING, "1000000", "EBIKE20261126101000X");
        VnPayReturnResponse response = service.handleReturnUrl(success("EBIKE20261126101000X", "100", "4242"));
        assertThat(response.valid()).isTrue();
        assertThat(response.success()).isFalse();
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void return_unknownReferenceIsValidButHasNoPayment() {
        when(paymentRepository.findOrderIdByTransactionReference("EBIKE-NOPE")).thenReturn(Optional.empty());
        VnPayReturnResponse response = service.handleReturnUrl(success("EBIKE-NOPE", "100", "1"));
        assertThat(response.valid()).isTrue();
        assertThat(response.success()).isFalse();
        assertThat(response.paymentId()).isNull();
        assertThat(response.orderId()).isNull();
        assertThat(response.paymentStatus()).isNull();
    }

    @Test
    void return_lateSuccessForACancelledOrderShowsSuccessOfThePaymentButKeepsTheOrderCancelled() {
        Order order = stubPendingPayment(OrderStatus.CANCELLED, PaymentMethod.VNPAY, PaymentStatus.CANCELLED, "1000000", "EBIKE20261126101000X");
        VnPayReturnResponse response = service.handleReturnUrl(success("EBIKE20261126101000X", "100000000", "4242"));
        assertThat(response.paymentStatus()).isEqualTo("PAID");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void return_returnWithoutAReferenceDoesNotThrow() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", sign(params, SECRET));
        VnPayReturnResponse response = service.handleReturnUrl(params);
        assertThat(response.valid()).isTrue();
        assertThat(response.success()).isFalse();
    }
}
