package com.ebike.service.order.impl;

import com.ebike.config.order.VnPayProperties;
import com.ebike.dto.order.request.VnPayCreatePaymentRequest;
import com.ebike.dto.order.response.VnPayCreatePaymentResponse;
import com.ebike.dto.order.response.VnPayReturnResponse;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.service.installment.InstallmentRules;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.service.order.VnPayService;
import com.ebike.util.order.VnPayUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VnPayServiceImpl implements VnPayService {

    private static final String SUCCESS_CODE = "00";
    private static final BigDecimal VNPAY_AMOUNT_MULTIPLIER = new BigDecimal("100");
    private static final ZoneId VNPAY_ZONE = VnPayUtil.ZONE;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(VnPayServiceImpl.class);

    private final VnPayProperties properties;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InstallmentApplicationRepository installmentApplications;
    private final ObjectMapper objectMapper;
    // how long an approved instalment application waits for its down payment (the expiry job and the order page read the same setting)
    private final Duration downPaymentHold;

    public VnPayServiceImpl(
        VnPayProperties properties,
        OrderRepository orderRepository,
        PaymentRepository paymentRepository,
        InstallmentApplicationRepository installmentApplications,
        ObjectMapper objectMapper,
        @Value("${app.order.installment-down-payment-hold:PT72H}") Duration downPaymentHold
    ) {
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.installmentApplications = installmentApplications;
        this.objectMapper = objectMapper;
        this.downPaymentHold = downPaymentHold;
    }

    @Override
    @Transactional
    public VnPayCreatePaymentResponse createPaymentUrl(
        VnPayCreatePaymentRequest request,
        String clientIp,
        String username,
        boolean backOfficeUser
    ) {
        validateConfigured();
        if (request == null || request.orderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }

        // the order row is locked (the first read of the transaction): a payment attempt resets the payment row, so it must not run in the middle of a cancellation
        Order order = orderRepository.findLockedById(request.orderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (
            !backOfficeUser
                && order.getUser() != null
                && (username == null || !username.equals(order.getUser().getUsername()))
        ) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only pay your own orders");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, com.ebike.common.i18n.RequestLanguage.text(
                "Đơn hàng đã bị hủy nên không thanh toán được.", "Cannot pay a cancelled order."));
        }
        Payment existingPayment = paymentRepository.findByOrderId(request.orderId()).orElse(null);
        // V24: an instalment order (its payment row is an INSTALLMENT one, or it has an application) is paid only after the staff approved the application,
        // only for the down payment, and only until the down payment is due. An instalment payment whose application row is missing is refused too.
        InstallmentApplication application = installmentApplications.findByOrderId(order.getId()).orElse(null);
        boolean installmentOrder = application != null || (existingPayment != null && existingPayment.getPaymentMethod() == PaymentMethod.INSTALLMENT);
        if (installmentOrder) {
            if (application == null || !InstallmentApplication.APPROVED.equals(application.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, com.ebike.common.i18n.RequestLanguage.text(
                    "Hồ sơ trả góp chưa được duyệt nên chưa thanh toán được khoản trả trước.", "The instalment application is not approved yet, so the down payment cannot be paid."));
            }
            if (InstallmentRules.downPaymentOverdue(application.getReviewedAt(), downPaymentHold, LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, com.ebike.common.i18n.RequestLanguage.text(
                    "Đã quá hạn thanh toán khoản trả trước nên đơn hàng sắp bị hủy tự động.", "The down payment is overdue, so the order is about to be cancelled automatically."));
            }
        }

        Payment payment = existingPayment != null ? prepareExistingPayment(existingPayment, order) : createPendingPayment(order);

        LocalDateTime now = LocalDateTime.now(VNPAY_ZONE);
        LocalDateTime expireTime = now.plus(VnPayUtil.PAYMENT_WINDOW);
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", toVnPayAmount(payment.getAmount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getTransactionReference());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderNumber() + " - Kinetic E-Bike");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", normalizeLocale(request.language()));
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", VnPayUtil.formatDate(now));
        params.put("vnp_ExpireDate", VnPayUtil.formatDate(expireTime));
        if (request.bankCode() != null && !request.bankCode().isBlank()) {
            params.put("vnp_BankCode", request.bankCode().trim());
        }

        String queryString = VnPayUtil.buildQuery(params);
        String secureHash = VnPayUtil.hmacSHA512(properties.getHashSecret(), queryString);
        String paymentUrl = properties.getPayUrl() + "?" + queryString + "&vnp_SecureHash=" + secureHash;

        Payment savedPayment = paymentRepository.save(payment);
        return new VnPayCreatePaymentResponse(
            paymentUrl,
            savedPayment.getId(),
            savedPayment.getTransactionReference(),
            savedPayment.getAmount(),
            expireTime,
            order.getOrderNumber()
        );
    }

    // READ_COMMITTED: the order is found through a bare id first; with the default snapshot, what is read after waiting for the order lock could be older than the lock
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, String> handleIpnCallback(Map<String, String> params) {
        if (!isValidSignature(params)) {
            return response("97", "Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        Payment payment = lockedPaymentOf(txnRef);
        if (payment == null) {
            return response("01", "Order not found");
        }
        if (!awaitsGatewayResult(payment)) {
            recordDuplicatePayment(payment, params);
            return response(SUCCESS_CODE, "Order already confirmed");
        }
        if (!amountMatches(payment, params.get("vnp_Amount"))) {
            return response("04", "Invalid Amount");
        }

        applyGatewayResult(payment, params);
        return response(SUCCESS_CODE, isGatewaySuccess(params) ? "Confirm Success" : "Confirm Failed");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VnPayReturnResponse handleReturnUrl(Map<String, String> params) {
        boolean valid = isValidSignature(params);
        Payment payment = valid ? lockedPaymentOf(params.get("vnp_TxnRef")) : null;

        if (payment != null && awaitsGatewayResult(payment) && amountMatches(payment, params.get("vnp_Amount"))) {
            applyGatewayResult(payment, params);
        } else if (payment != null && !awaitsGatewayResult(payment)) {
            recordDuplicatePayment(payment, params);
        }

        BigDecimal amount = parseVnPayAmount(params.get("vnp_Amount"));
        boolean success = valid && payment != null && payment.getPaymentStatus() == PaymentStatus.PAID;
        Order order = payment == null ? null : payment.getOrder();
        return new VnPayReturnResponse(
            valid,
            success,
            params.get("vnp_ResponseCode"),
            params.get("vnp_TxnRef"),
            amount,
            params.get("vnp_TransactionNo"),
            params.get("vnp_BankCode"),
            params.get("vnp_CardType"),
            payment == null ? null : payment.getId(),
            order == null ? null : order.getId(),
            order == null ? null : order.getOrderNumber(),
            payment == null ? null : payment.getPaymentStatus().name()
        );
    }

    /**
     * The payment of a gateway reference, read after its order row is locked (the order first, then the payment, as every cancellation takes them), so
     * a cancellation, the expiry job or the staff's confirmation running at the same moment is seen instead of being written over. Null when there is none.
     */
    private Payment lockedPaymentOf(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }
        Long orderId = paymentRepository.findOrderIdByTransactionReference(txnRef).orElse(null);
        if (orderId == null) {
            return null;
        }
        orderRepository.findLockedById(orderId);
        return paymentRepository.findByTransactionReference(txnRef).orElse(null);
    }

    private Payment prepareExistingPayment(Payment payment, Order order) {
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, com.ebike.common.i18n.RequestLanguage.text(
                payment.getPaymentMethod() == PaymentMethod.INSTALLMENT ? "Khoản trả trước của đơn này đã được thanh toán." : "Đơn hàng đã được thanh toán.",
                payment.getPaymentMethod() == PaymentMethod.INSTALLMENT ? "The down payment of this order is already paid." : "Order is already paid."));
        }
        // V24: the payment row of an instalment order is its down payment: it keeps its amount and its method, only the gateway attempt is new
        boolean installment = payment.getPaymentMethod() == PaymentMethod.INSTALLMENT;
        BigDecimal amountDue = installment ? payment.getAmount() : order.getTotalAmount();
        if (payment.getPaymentStatus() == PaymentStatus.PENDING && (payment.getPaymentMethod() == PaymentMethod.VNPAY || installment)) {
            payment.setAmount(amountDue);
            payment.setCurrency("VND");
            payment.setTransactionReference(generateTxnRef(order));
            return payment;
        }
        if (!installment) {
            payment.setPaymentMethod(PaymentMethod.VNPAY);
        }
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(amountDue);
        payment.setCurrency("VND");
        payment.setTransactionReference(generateTxnRef(order));
        payment.setProviderTxnId(null);
        payment.setProviderResponse(null);
        payment.setVnpayResponseCode(null);
        payment.setVnpayBankCode(null);
        payment.setVnpayCardType(null);
        payment.setVnpaySecureHash(null);
        payment.setPaidAt(null);
        return payment;
    }

    private Payment createPendingPayment(Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("VND");
        payment.setTransactionReference(generateTxnRef(order));
        return payment;
    }

    private String generateTxnRef(Order order) {
        String timestamp = LocalDateTime.now(VNPAY_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String orderNumber = order.getOrderNumber().replaceAll("[^A-Za-z0-9]", "");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "EBIKE" + timestamp + orderNumber + suffix;
    }

    /**
     * A VNPay result still has to be recorded when the payment is waiting, or when the order was cancelled
     * (for example by the unpaid-order expiry) while the customer was still on the gateway page.
     */
    private boolean awaitsGatewayResult(Payment payment) {
        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            return true;
        }
        return payment.getPaymentStatus() == PaymentStatus.CANCELLED
            && (payment.getPaymentMethod() == PaymentMethod.VNPAY || payment.getPaymentMethod() == PaymentMethod.INSTALLMENT)
            && payment.getOrder().getStatus() == OrderStatus.CANCELLED;
    }

    /**
     * A successful gateway payment arrived for a payment that is already paid by another channel (for example the staff took the down payment at the
     * store while the customer was on the VNPay page): the customer has paid twice. Nothing else would show it, so it is written into the payment's
     * provider response, where the staff see it and can refund it. The same payment arriving again (the IPN retry, then the return page) is not a duplicate.
     */
    private void recordDuplicatePayment(Payment payment, Map<String, String> params) {
        String transactionNo = params.get("vnp_TransactionNo");
        if (payment.getPaymentStatus() != PaymentStatus.PAID || !isGatewaySuccess(params) || !amountMatches(payment, params.get("vnp_Amount"))
            || transactionNo == null || transactionNo.isBlank() || transactionNo.equals(payment.getProviderTxnId())) {
            return;
        }
        String existing = payment.getProviderResponse() == null ? "" : payment.getProviderResponse();
        String marker = "DUPLICATE VNPAY PAYMENT " + transactionNo;
        if (existing.contains(marker)) {
            return;
        }
        payment.setProviderResponse((existing.isEmpty() ? "" : existing + "\n") + marker + " - refund it: " + toJson(params));
        paymentRepository.save(payment);
        LOGGER.warn("Order {} was paid twice: VNPay transaction {} came in after the payment was already confirmed", payment.getOrder().getOrderNumber(), transactionNo);
    }

    private void applyGatewayResult(Payment payment, Map<String, String> params) {
        payment.setProviderTxnId(params.get("vnp_TransactionNo"));
        payment.setProviderResponse(toJson(params));
        payment.setVnpayResponseCode(params.get("vnp_ResponseCode"));
        payment.setVnpayBankCode(params.get("vnp_BankCode"));
        payment.setVnpayCardType(params.get("vnp_CardType"));
        payment.setVnpaySecureHash(params.get("vnp_SecureHash"));

        boolean orderCancelled = payment.getOrder().getStatus() == OrderStatus.CANCELLED;
        if (isGatewaySuccess(params)) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            // A cancelled order stays cancelled (its stock is already back on sale);
            // the money shows up as PAID on a cancelled order so the manager refunds it (UC-17).
            if (!orderCancelled) {
                payment.getOrder().setStatus(OrderStatus.CONFIRMED);
            }
        } else if (!orderCancelled) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
        }
        paymentRepository.save(payment);
    }

    private boolean isValidSignature(Map<String, String> params) {
        String providedHash = params.get("vnp_SecureHash");
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }
        Map<String, String> signedParams = new HashMap<>(params);
        signedParams.remove("vnp_SecureHash");
        signedParams.remove("vnp_SecureHashType");
        String calculatedHash = VnPayUtil.hmacSHA512(properties.getHashSecret(), VnPayUtil.buildQuery(signedParams));
        return calculatedHash.equalsIgnoreCase(providedHash);
    }

    private boolean isGatewaySuccess(Map<String, String> params) {
        return SUCCESS_CODE.equals(params.get("vnp_ResponseCode")) && SUCCESS_CODE.equals(params.get("vnp_TransactionStatus"));
    }

    private boolean amountMatches(Payment payment, String rawAmount) {
        BigDecimal amount = parseVnPayAmount(rawAmount);
        return amount != null && payment.getAmount().compareTo(amount) == 0;
    }

    private BigDecimal parseVnPayAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawAmount).divide(VNPAY_AMOUNT_MULTIPLIER, 2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private String toVnPayAmount(BigDecimal amount) {
        return amount.multiply(VNPAY_AMOUNT_MULTIPLIER).setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private String normalizeLocale(String language) {
        if (language == null || language.isBlank()) {
            return "vn";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return "en".equals(normalized) ? "en" : "vn";
    }

    private Map<String, String> response(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException exception) {
            return params.toString();
        }
    }

    private void validateConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "VNPay is not configured");
        }
    }
}
