package com.ebike.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** V22: the back-in-stock email. A missing SMTP setup or a send failure is logged and never stops the stock update. */
@Service
public class StockAlertEmailSender {

    private static final Logger log = LoggerFactory.getLogger(StockAlertEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;

    public StockAlertEmailSender(
        JavaMailSender mailSender,
        @Value("${app.order.email-otp.from:no-reply@ebike.local}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.order.email-otp.mail-enabled:true}") boolean mailEnabled
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
    }

    public void send(String email, String productName, String colorName) {
        if (!mailEnabled || smtpHost == null || smtpHost.isBlank() || email == null || email.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Kinetic E-Bike: " + productName + " (" + colorName + ") đã có hàng lại");
        message.setText("""
            Xe %s, màu %s mà bạn đang chờ đã có hàng lại.

            Vào cửa hàng Kinetic và mở trang xe để đặt mua. Số lượng có hạn, bạn nên đặt sớm. Bạn nhận email này vì đã chọn "Báo tôi khi có hàng".
            """.formatted(productName, colorName));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Could not send back-in-stock email to {}: {}", email, exception.getMessage());
        }
    }
}
