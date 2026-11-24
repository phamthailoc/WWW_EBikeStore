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
    private final String publicBaseUrl;

    public StockAlertEmailSender(
        JavaMailSender mailSender,
        @Value("${app.stock-alert.from:${app.order.email-otp.from:no-reply@ebike.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.stock-alert.mail-enabled:true}") boolean mailEnabled,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** {@code userId} is only for the log: a customer's address does not belong in the log file. */
    public void send(Long userId, String email, String productName, String colorName, String productSlug) {
        if (!mailEnabled || smtpHost == null || smtpHost.isBlank() || email == null || email.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject(StockAlertMessages.emailSubject(productName, colorName));
        message.setText(StockAlertMessages.emailBody(productName, colorName, StockAlertMessages.productUrl(publicBaseUrl, productSlug)));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // The exception text is left out: mail failures usually quote the recipient's address.
            log.warn("Could not send back-in-stock email to customer {} ({})", userId, exception.getClass().getSimpleName());
        }
    }
}
