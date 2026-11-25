package com.ebike.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** The welcome email after sign-up. A missing SMTP setup or a send failure is logged and never undoes the registration. */
@Service
public class AccountEmailSender {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;
    private final String publicBaseUrl;

    public AccountEmailSender(
        JavaMailSender mailSender,
        @Value("${app.account.from:${app.order.email-otp.from:no-reply@ebike.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.account.mail-enabled:true}") boolean mailEnabled,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean canSend() {
        return mailEnabled && smtpHost != null && !smtpHost.isBlank();
    }

    /** Returns true when the message was handed to the SMTP server. */
    public boolean sendWelcome(String email, String displayName, String username) {
        if (!canSend() || email == null || email.isBlank()) {
            log.debug("Welcome email skipped: mail is off, there is no SMTP host or the account has no email");
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Chào mừng bạn đến với KINETIC");
        message.setText(welcomeBody(displayName, username, email, loginUrl(publicBaseUrl)));
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            log.warn("Could not send the welcome email: {}", exception.getMessage());
            return false;
        }
    }

    static String welcomeBody(String displayName, String username, String email, String loginUrl) {
        StringBuilder text = new StringBuilder();
        text.append("Xin chào ").append(displayName == null || displayName.isBlank() ? username : displayName).append(",\n\n");
        text.append("Tài khoản KINETIC của bạn đã được tạo thành công.\n");
        text.append("Tên đăng nhập: ").append(username).append('\n');
        text.append("Email: ").append(email).append("\n\n");
        text.append("Bạn có thể đăng nhập để đặt xe, theo dõi đơn hàng và đặt lịch lái thử.\n");
        if (loginUrl != null) {
            text.append(loginUrl).append('\n');
        }
        text.append("\nNếu bạn không tạo tài khoản này, vui lòng bỏ qua email.\n");
        return text.toString();
    }

    static String loginUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/login";
    }
}
