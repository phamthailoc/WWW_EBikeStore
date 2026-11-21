package com.ebike.service.warranty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** V20: the maintenance reminder email. A missing SMTP setup or a send failure is logged and never stops the reminder job. */
@Service
public class MaintenanceEmailSender {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;
    private final String publicBaseUrl;

    public MaintenanceEmailSender(
        JavaMailSender mailSender,
        @Value("${app.maintenance.from:${app.order.email-otp.from:no-reply@ebike.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.maintenance.mail-enabled:true}") boolean mailEnabled,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** {@code language} is the customer's saved language ("vi" or "en"). */
    public void send(String email, String productName, String frameNumber, String milestone, String language) {
        if (!mailEnabled || smtpHost == null || smtpHost.isBlank() || email == null || email.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject(WarrantyMessages.maintenanceEmailSubject(language, productName));
        message.setText(WarrantyMessages.maintenanceEmailBody(language, productName, frameNumber, milestone, WarrantyMessages.warrantyUrl(publicBaseUrl)));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Could not send maintenance email to {}: {}", email, exception.getMessage());
        }
    }
}
