package com.ebike.service.installment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** V24: the instalment decision emails. A missing SMTP setup or a send failure is logged and never stops the staff's decision. */
@Service
public class InstallmentEmailSender {

    private static final Logger log = LoggerFactory.getLogger(InstallmentEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;
    private final String publicBaseUrl;

    public InstallmentEmailSender(
        JavaMailSender mailSender,
        @Value("${app.installment.from:${app.order.email-otp.from:no-reply@ebike.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.installment.mail-enabled:true}") boolean mailEnabled,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** {@code kind} is a constant of InstallmentMessages. */
    public void send(String email, String kind, Long orderId, String orderNumber, BigDecimal downPayment, BigDecimal monthlyPayment,
                     int termMonths, OffsetDateTime payBefore, String note) {
        if (!mailEnabled || smtpHost == null || smtpHost.isBlank() || email == null || email.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject(InstallmentMessages.emailSubject(kind, orderNumber));
        message.setText(InstallmentMessages.emailBody(kind, orderNumber, downPayment, monthlyPayment, termMonths, payBefore, note,
            InstallmentMessages.orderUrl(publicBaseUrl, orderId)));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Could not send instalment email: {}", exception.getMessage());
        }
    }
}
