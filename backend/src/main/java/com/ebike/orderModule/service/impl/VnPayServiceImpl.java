package com.ebike.orderModule.service.impl;

import com.ebike.orderModule.config.VnPayProperties;
import com.ebike.orderModule.dto.request.VnPayCreatePaymentRequest;
import com.ebike.orderModule.dto.response.VnPayCreatePaymentResponse;
import com.ebike.orderModule.dto.response.VnPayReturnResponse;
import com.ebike.orderModule.entity.Order;
import com.ebike.orderModule.entity.OrderStatus;
import com.ebike.orderModule.entity.Payment;
import com.ebike.orderModule.entity.PaymentMethod;
import com.ebike.orderModule.entity.PaymentStatus;
import com.ebike.orderModule.repository.OrderRepository;
import com.ebike.orderModule.repository.PaymentRepository;
import com.ebike.orderModule.service.VnPayService;
import com.ebike.orderModule.util.VnPayUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VnPayServiceImpl implements VnPayService {

    private static final String SUCCESS_CODE = "00";
    private static final BigDecimal VNPAY_AMOUNT_MULTIPLIER = new BigDecimal("100");
    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VnPayProperties properties;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public VnPayServiceImpl(
        VnPayProperties properties,
        OrderRepository orderRepository,
        PaymentRepository paymentRepository,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
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

        Order order = orderRepository.findById(request.orderId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (
            !backOfficeUser
                && order.getUser() != null
                && (username == null || !username.equals(order.getUser().getUsername()))
        ) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only pay your own orders");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot pay a cancelled order");
        }

        Payment payment = paymentRepository.findByOrderId(request.orderId())
            .map(existing -> prepareExistingPayment(existing, order))
            .orElseGet(() -> createPendingPayment(order));

        LocalDateTime now = LocalDateTime.now(VNPAY_ZONE);
        LocalDateTime expireTime = now.plusMinutes(15);
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

    @Override
    @Transactional
    public Map<String, String> handleIpnCallback(Map<String, String> params) {
        if (!isValidSignature(params)) {
            return response("97", "Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        Payment payment = paymentRepository.findByTransactionReference(txnRef).orElse(null);
        if (payment == null) {
            return response("01", "Order not found");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            return response(SUCCESS_CODE, "Order already confirmed");
        }
        if (!amountMatches(payment, params.get("vnp_Amount"))) {
            return response("04", "Invalid Amount");
        }

        applyGatewayResult(payment, params);
        return response(SUCCESS_CODE, isGatewaySuccess(params) ? "Confirm Success" : "Confirm Failed");
    }

    @Override
    @Transactional
    public VnPayReturnResponse handleReturnUrl(Map<String, String> params) {
        boolean valid = isValidSignature(params);
        Payment payment = valid
            ? paymentRepository.findByTransactionReference(params.get("vnp_TxnRef")).orElse(null)
            : null;

        if (payment != null && payment.getPaymentStatus() == PaymentStatus.PENDING && amountMatches(payment, params.get("vnp_Amount"))) {
            applyGatewayResult(payment, params);
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

    private Payment prepareExistingPayment(Payment payment, Order order) {
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is already paid");
        }
        if (payment.getPaymentStatus() == PaymentStatus.PENDING && payment.getPaymentMethod() == PaymentMethod.VNPAY) {
            payment.setAmount(order.getTotalAmount());
            payment.setCurrency("VND");
            payment.setTransactionReference(generateTxnRef(order));
            return payment;
        }
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotalAmount());
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

    private void applyGatewayResult(Payment payment, Map<String, String> params) {
        payment.setProviderTxnId(params.get("vnp_TransactionNo"));
        payment.setProviderResponse(toJson(params));
        payment.setVnpayResponseCode(params.get("vnp_ResponseCode"));
        payment.setVnpayBankCode(params.get("vnp_BankCode"));
        payment.setVnpayCardType(params.get("vnp_CardType"));
        payment.setVnpaySecureHash(params.get("vnp_SecureHash"));

        if (isGatewaySuccess(params)) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            payment.getOrder().setStatus(OrderStatus.CONFIRMED);
        } else {
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
        if (isBlank(properties.getTmnCode()) || isBlank(properties.getHashSecret()) || isBlank(properties.getPayUrl()) || isBlank(properties.getReturnUrl())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "VNPay is not configured");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
