package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** V20: the maintenance email is sent only when mail is on and an SMTP host exists; a send failure never reaches the reminder job. */
class MaintenanceEmailSenderTest {

    private JavaMailSender mail;

    @BeforeEach
    void setUp() {
        mail = mock(JavaMailSender.class);
    }

    private MaintenanceEmailSender sender(String host, boolean enabled, String baseUrl) {
        return new MaintenanceEmailSender(mail, "shop@example.com", host, enabled, baseUrl);
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void nothingIsSentWhenMailIsSwitchedOff() {
        sender("smtp.example.com", false, "").send("an@example.com", "Bike", "F1", "P6M", "vi");

        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void nothingIsSentWithoutAnSmtpHost() {
        sender("", true, "").send("an@example.com", "Bike", "F1", "P6M", "vi");
        sender(null, true, "").send("an@example.com", "Bike", "F1", "P6M", "vi");

        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void nothingIsSentWithoutAnAddress() {
        sender("smtp.example.com", true, "").send(" ", "Bike", "F1", "P6M", "vi");
        sender("smtp.example.com", true, "").send(null, "Bike", "F1", "P6M", "vi");

        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void theEmailLinksToTheWarrantyBookWhenThePublicAddressIsKnown() {
        sender("smtp.example.com", true, "https://shop.example.com/").send("an@example.com", "Bike", "F1", "P6M", "en");

        SimpleMailMessage message = sent();
        assertEquals("shop@example.com", message.getFrom());
        assertEquals("an@example.com", message.getTo()[0]);
        assertTrue(message.getSubject().contains("due for service"));
        assertTrue(message.getText().contains("https://shop.example.com/customer/warranty"));
    }

    @Test
    void withoutAPublicAddressTheEmailPointsToThePageByName() {
        sender("smtp.example.com", true, "").send("an@example.com", "Bike", "F1", "P6M", "vi");

        SimpleMailMessage message = sent();
        assertTrue(message.getSubject().contains("đến hạn bảo dưỡng"));
        assertFalse(message.getText().contains("http"));
        assertTrue(message.getText().contains("trang Bảo hành"));
    }

    @Test
    void aMailFailureIsSwallowed() {
        doThrow(new MailSendException("down")).when(mail).send(any(SimpleMailMessage.class));

        sender("smtp.example.com", true, "").send("an@example.com", "Bike", "F1", "P6M", "vi");

        verify(mail).send(any(SimpleMailMessage.class));
    }
}
