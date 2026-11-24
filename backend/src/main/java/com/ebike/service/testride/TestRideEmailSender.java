package com.ebike.service.testride;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** V23: the test-ride emails. A missing SMTP setup or a send failure is logged and never stops the booking or the reminder job. */
@Service
public class TestRideEmailSender {

    private static final Logger log = LoggerFactory.getLogger(TestRideEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;
    private final String publicBaseUrl;

    public TestRideEmailSender(
        JavaMailSender mailSender,
        @Value("${app.test-ride.from:${app.order.email-otp.from:no-reply@ebike.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.test-ride.mail-enabled:true}") boolean mailEnabled,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** {@code kind} is a constant of TestRideMessages. */
    public void send(String email, String kind, String productName, String showroomName, String address, LocalDateTime slotStart, String note) {
        if (!mailEnabled || smtpHost == null || smtpHost.isBlank() || email == null || email.isBlank() || !TestRideMessages.sendsEmail(kind)) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject(TestRideMessages.emailSubject(kind, productName));
        message.setText(TestRideMessages.emailBody(kind, productName, showroomName, address, slotStart, note, TestRideMessages.bookingsUrl(publicBaseUrl)));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Could not send test ride email: {}", exception.getMessage());
        }
    }
}
