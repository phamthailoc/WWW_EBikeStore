package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/** V27: the hand-over email is sent only when mail is on and an SMTP host exists, carries both PDFs, and a send failure never reaches the caller. */
class DeliveryEmailSenderTest {

    private JavaMailSender mail;

    @BeforeEach
    void setUp() {
        mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
    }

    private DeliveryEmailSender sender(String host, boolean enabled, String baseUrl) {
        return new DeliveryEmailSender(mail, DocumentFixtures.store(), "shop@example.com", host, enabled, baseUrl);
    }

    private static OrderDocumentService.DeliveryDocuments documents(String email, boolean card) {
        List<PdfFile> files = card
            ? List.of(new PdfFile("receipt-ORD-1.pdf", new byte[] {1, 2, 3}), new PdfFile("warranty-card-ORD-1.pdf", new byte[] {4, 5}))
            : List.of(new PdfFile("receipt-ORD-1.pdf", new byte[] {1, 2, 3}));
        return new OrderDocumentService.DeliveryDocuments(email, "Lan", "ORD-1", files, false, card);
    }

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(captor.capture());
        return captor.getValue();
    }

    private static List<String> attachmentNames(Multipart multipart) throws Exception {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        collect(multipart, names);
        return names;
    }

    private static void collect(Multipart multipart, List<String> names) throws Exception {
        for (int index = 0; index < multipart.getCount(); index++) {
            var part = multipart.getBodyPart(index);
            if (part.getFileName() != null) {
                names.add(part.getFileName());
            } else if (part.getContent() instanceof Multipart nested) {
                collect(nested, names);
            }
        }
    }

    @Test
    void theBodyIsVietnameseByDefaultAndLinksToTheOrdersWhenTheAddressIsKnown() {
        DeliveryEmailSender sender = sender("smtp.example.com", true, "https://shop.example.com/");

        String body = sender.body(documents("lan@example.com", true));

        assertTrue(body.startsWith("Xin chào Lan,"), body);
        assertTrue(body.contains("Phiếu bảo hành cho từng xe"), body);
        assertTrue(body.contains("không phải hóa đơn điện tử"), body);
        assertTrue(body.contains("https://shop.example.com/customer/orders"), body);
    }

    @Test
    void aGuestOrderEmailHasNoSignInLine() {
        DeliveryEmailSender sender = sender("smtp.example.com", true, "https://shop.example.com");
        var guest = new OrderDocumentService.DeliveryDocuments("khach@example.com", "Khach", "ORD-1", List.of(new PdfFile("receipt-ORD-1.pdf", new byte[] {1})), true, false);

        String body = sender.body(guest);

        assertFalse(body.contains("customer/orders"), body);
    }

    @Test
    void nothingIsSentWhenMailIsOffTheHostIsMissingOrThereIsNoAddress() {
        assertFalse(sender("smtp.example.com", false, "").send(documents("lan@example.com", true)));
        assertFalse(sender("", true, "").send(documents("lan@example.com", true)));
        assertFalse(sender(null, true, "").send(documents("lan@example.com", true)));
        assertFalse(sender("smtp.example.com", true, "").send(documents(" ", true)));
        assertFalse(sender("smtp.example.com", true, "").send(documents(null, true)));
        assertFalse(sender("smtp.example.com", true, "").send(null));

        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    void aSendFailureIsSwallowed() throws IOException {
        doThrow(new MailSendException("down")).when(mail).send(any(MimeMessage.class));

        assertFalse(sender("smtp.example.com", true, "").send(documents("lan@example.com", true)));
    }

    @Test
    void theMessageHasBothFilesAndIsWordedInVietnamese() throws Exception {
        assertTrue(sender("smtp.example.com", true, "https://shop.example.com/").send(documents("lan@example.com", true)));
        MimeMessage message = sent();
        message.saveChanges();
        assertEquals("shop@example.com", message.getFrom()[0].toString());
        assertEquals("lan@example.com", message.getAllRecipients()[0].toString());
        assertTrue(message.getSubject().endsWith("phiếu bán hàng và phiếu bảo hành cho đơn ORD-1"), message.getSubject());
        Multipart parts = (Multipart) message.getContent();
        assertTrue(attachmentNames(parts).containsAll(List.of("receipt-ORD-1.pdf", "warranty-card-ORD-1.pdf")), attachmentNames(parts).toString());
    }

    @Test
    void withoutACardTheBodyAndSubjectNameOnlyTheReceiptAndWithoutAnAddressThereIsNoLink() {
        DeliveryEmailSender sender = sender("smtp.example.com", true, "");
        String body = sender.body(documents("lan@example.com", false));
        assertFalse(body.contains("Phiếu bảo hành cho từng xe"), body);
        assertFalse(body.contains("http"), body);
        assertTrue(sender.subject("ORD-1", false).endsWith("phiếu bán hàng cho đơn ORD-1"), sender.subject("ORD-1", false));
    }
}
