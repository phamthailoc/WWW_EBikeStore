package com.ebike.service.testride;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** V23: the test-ride email is sent only when mail is on, an SMTP host exists and the kind is one that is emailed; a failure never reaches the caller. */
class TestRideEmailSenderTest {

    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 11, 22, 9, 0);
    private JavaMailSender mail;

    @BeforeEach
    void setUp() {
        mail = mock(JavaMailSender.class);
    }

    private TestRideEmailSender sender(String host, boolean enabled) {
        return new TestRideEmailSender(mail, "shop@example.com", host, enabled, "https://shop.example");
    }

    @Test
    void nothingIsSentWhenMailIsOffOrThereIsNoHostOrNoAddress() {
        sender("smtp.example.com", false).send("an@example.com", TestRideMessages.CONFIRMED, "Bike", "Quan 1", "addr", SLOT, null);
        sender("", true).send("an@example.com", TestRideMessages.CONFIRMED, "Bike", "Quan 1", "addr", SLOT, null);
        sender(null, true).send("an@example.com", TestRideMessages.CONFIRMED, "Bike", "Quan 1", "addr", SLOT, null);
        sender("smtp.example.com", true).send(" ", TestRideMessages.CONFIRMED, "Bike", "Quan 1", "addr", SLOT, null);
        sender("smtp.example.com", true).send(null, TestRideMessages.CONFIRMED, "Bike", "Quan 1", "addr", SLOT, null);
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void kindsThatAreOnlyInAppAreNotEmailed() {
        sender("smtp.example.com", true).send("an@example.com", TestRideMessages.NO_SHOW, "Bike", "Quan 1", "addr", SLOT, null);
        sender("smtp.example.com", true).send("an@example.com", TestRideMessages.EXPIRED, "Bike", "Quan 1", "addr", SLOT, null);
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void aSendFailureIsSwallowed() {
        doThrow(new MailSendException("smtp down")).when(mail).send(any(SimpleMailMessage.class));
        sender("smtp.example.com", true).send("an@example.com", TestRideMessages.REMINDER, "Kinetic X", "Quan 1", null, SLOT, null);
        verify(mail).send(any(SimpleMailMessage.class));
    }
}
