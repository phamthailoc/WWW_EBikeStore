package com.ebike.service.document;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** V27: the hand-over email with the sales receipt and the warranty card attached. A missing SMTP setup or a send failure is logged and never stops anything. */
@Service
public class DeliveryEmailSender {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;
    private final String publicBaseUrl;
    private final StoreProfile store;

    public DeliveryEmailSender(
        JavaMailSender mailSender,
        StoreProfile store,
        @Value("${app.documents.from:${app.order.email-otp.from:no-reply@ebike.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.documents.mail-enabled:true}") boolean mailEnabled,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.mailSender = mailSender;
        this.store = store;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
        this.publicBaseUrl = publicBaseUrl == null || StoreProfile.isLocalAddress(publicBaseUrl) ? "" : publicBaseUrl.trim();
    }

    /** False when mail is turned off or there is no SMTP host: then nothing is worth rendering, because nothing can be sent. */
    public boolean canSend() {
        return mailEnabled && smtpHost != null && !smtpHost.isBlank();
    }

    /** Returns true when the message was handed to the mail server. */
    public boolean send(OrderDocumentService.DeliveryDocuments documents) {
        if (!canSend() || documents == null || documents.email() == null || documents.email().isBlank()) {
            return false;
        }
        String language = documents.language();
        boolean hasCard = documents.files().stream().anyMatch(file -> file.filename().startsWith("warranty-card"));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(documents.email().trim());
            helper.setSubject(subject(language, documents.orderNumber(), hasCard));
            helper.setText(body(language, documents, hasCard), false);
            for (PdfFile file : documents.files()) {
                helper.addAttachment(file.filename(), new ByteArrayResource(file.content()), "application/pdf");
            }
            mailSender.send(message);
            return true;
        } catch (MessagingException | MailException exception) {
            log.warn("Could not send the hand-over email for order {}: {}", documents.orderNumber(), exception.getMessage());
            return false;
        }
    }

    String subject(String language, String orderNumber, boolean hasCard) {
        return DocumentText.text(language, hasCard ? "mail.subject" : "mail.subjectReceiptOnly", store.getName(), orderNumber);
    }

    String body(String language, OrderDocumentService.DeliveryDocuments documents, boolean hasCard) {
        StringBuilder text = new StringBuilder();
        text.append(DocumentText.text(language, "mail.greeting", documents.recipientName())).append("\n\n");
        text.append(DocumentText.text(language, "mail.body", documents.orderNumber(), store.getName())).append("\n\n");
        text.append(DocumentText.text(language, "mail.attached")).append('\n');
        text.append(DocumentText.text(language, "mail.receipt")).append('\n');
        if (hasCard) {
            text.append(DocumentText.text(language, "mail.card")).append('\n');
        }
        text.append('\n').append(DocumentText.text(language, "mail.notInvoice")).append("\n\n");
        if (!documents.guest() && !publicBaseUrl.isEmpty()) {
            String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
            text.append(DocumentText.text(language, "mail.signIn", base + "/customer/orders")).append("\n\n");
        }
        text.append(DocumentText.text(language, "mail.reason", store.getName())).append('\n');
        return text.toString();
    }
}
