package com.ebike.service.order.impl;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.Shipment;
import com.ebike.repository.order.OrderRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "order received" email: order number, lines, totals and how the customer gets the bike. It runs after the checkout commit on the mail pool,
 * so a missing SMTP setup (the default local setup) or a refused message is logged and never touches the order.
 */
@Service
public class OrderPlacedEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedEmailService.class);

    private final OrderRepository orders;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;
    private final String publicBaseUrl;

    public OrderPlacedEmailService(
        OrderRepository orders,
        JavaMailSender mailSender,
        @Value("${app.order.confirmation.from:${app.order.email-otp.from:no-reply@ebike.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.order.confirmation.mail-enabled:true}") boolean mailEnabled,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.orders = orders;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public boolean send(Long orderId) {
        if (!mailEnabled || smtpHost == null || smtpHost.isBlank()) {
            log.debug("Order confirmation of order id {} skipped: mail is off or there is no SMTP host", orderId);
            return false;
        }
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        String to = recipient(order);
        if (to == null) {
            log.debug("Order confirmation of {} skipped: the order has no email address", order.getOrderNumber());
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("KINETIC đã nhận đơn hàng " + order.getOrderNumber());
        message.setText(body(order, order.getUser() == null ? null : orderUrl(publicBaseUrl, order.getId())));
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            log.warn("Could not send the confirmation of order {}: {}", order.getOrderNumber(), exception.getMessage());
            return false;
        }
    }

    static String recipient(Order order) {
        if (notBlank(order.getCustomerEmail())) {
            return order.getCustomerEmail().trim();
        }
        Shipment shipment = order.getShipment();
        if (shipment != null && notBlank(shipment.getRecipientEmail())) {
            return shipment.getRecipientEmail().trim();
        }
        return order.getUser() != null && notBlank(order.getUser().getEmail()) ? order.getUser().getEmail().trim() : null;
    }

    static String body(Order order, String orderUrl) {
        Shipment shipment = order.getShipment();
        StringBuilder text = new StringBuilder();
        String name = shipment != null && notBlank(shipment.getRecipientName()) ? shipment.getRecipientName().trim() : null;
        text.append("Xin chào").append(name == null ? "" : " " + name).append(",\n\n");
        text.append("Cảm ơn bạn đã đặt xe tại KINETIC. Đơn hàng ").append(order.getOrderNumber()).append(" đang chờ xác nhận.\n\n");
        for (OrderItem item : order.getItems()) {
            text.append("- ").append(item.getProductName()).append(" x ").append(item.getQuantity()).append(": ").append(money(item.getLineTotal())).append('\n');
        }
        text.append('\n');
        text.append("Tạm tính: ").append(money(order.getSubtotal())).append('\n');
        text.append("Phí giao xe: ").append(money(order.getShippingFee())).append('\n');
        if (order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0) {
            text.append("Giảm giá: -").append(money(order.getDiscountAmount())).append('\n');
        }
        if (order.getRegistrationFee() != null && order.getRegistrationFee().signum() > 0) {
            text.append("Dịch vụ đăng ký xe: ").append(money(order.getRegistrationFee())).append('\n');
        }
        text.append("Tổng thanh toán: ").append(money(order.getTotalAmount())).append("\n\n");
        if (order.getPayment() != null && order.getPayment().getPaymentMethod() != null) {
            text.append("Phương thức thanh toán: ").append(paymentLabel(order.getPayment().getPaymentMethod())).append('\n');
        }
        if (shipment != null) {
            if (shipment.getPickupShowroom() != null) {
                text.append("Nhận tại showroom: ").append(shipment.getPickupShowroom().getName()).append(", ")
                    .append(shipment.getPickupShowroom().getAddress()).append('\n');
            } else if (notBlank(shipment.getShippingAddress())) {
                text.append("Giao đến: ").append(shipment.getShippingAddress()).append('\n');
            }
        }
        if (orderUrl != null) {
            text.append("\nTheo dõi đơn hàng: ").append(orderUrl).append('\n');
        }
        text.append("\nChúng tôi sẽ liên hệ khi đơn hàng được xác nhận.\n");
        return text.toString();
    }

    static String paymentLabel(PaymentMethod method) {
        return switch (method) {
            case PAY_LATER -> "Thanh toán sau (khi nhận xe)";
            case VNPAY -> "VNPay";
            case INSTALLMENT -> "Trả góp";
        };
    }

    static String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " ₫";
    }

    static String orderUrl(String baseUrl, Long orderId) {
        if (baseUrl == null || baseUrl.isBlank() || orderId == null) {
            return null;
        }
        String base = baseUrl.trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/customer/orders/" + orderId;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
