package com.ebike.orderModule.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderEmailSender {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;

    public OrderEmailSender(
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

    public void sendOtp(String email, String code) {
        if (!mailEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email OTP đang bị tắt. Vui lòng bật ORDER_EMAIL_OTP_MAIL_ENABLED=true.");
        }
        if (smtpHost == null || smtpHost.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình SMTP_HOST nên không thể gửi OTP qua email.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Mã xác thực đặt hàng Kinetic E-Bike");
        message.setText("""
            Mã OTP xác thực đơn hàng của bạn là: %s

            Mã có hiệu lực trong 15 phút. Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email.
            """.formatted(code));

        try {
            mailSender.send(message);
            log.info("Sent order email OTP to {}", email);
        } catch (MailException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể gửi OTP qua email. Vui lòng kiểm tra cấu hình SMTP.", exception);
        }
    }
}
