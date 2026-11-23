package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.service.admin.AuditLogService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** V27: staff send the hand-over email again, and learn at once when it cannot be sent. */
class DeliveryResendServiceTest {

    private final OrderDocumentService documents = mock(OrderDocumentService.class);
    private final DeliveryEmailSender sender = mock(DeliveryEmailSender.class);
    private final AuditLogService auditLog = mock(AuditLogService.class);
    private final DeliveryResendService service = new DeliveryResendService(documents, sender, auditLog);
    private final OrderDocumentService.DeliveryDocuments found =
        new OrderDocumentService.DeliveryDocuments("an@example.com", "An", "ORD-1", "vi", List.of(new PdfFile("receipt-ORD-1.pdf", new byte[] {1})));

    private static HttpStatus statusOf(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    @Test
    void theEmailIsSentAgainAndTheAddressIsReturned() {
        when(sender.canSend()).thenReturn(true);
        when(documents.forDeliveredOrder(5L)).thenReturn(Optional.of(found));
        when(sender.send(found)).thenReturn(true);

        assertEquals("an@example.com", service.resend(5L, "vi"));
        verify(auditLog).record("gửi lại email phiếu", "ORD-1");
    }

    @Test
    void withMailOffNothingIsRenderedOrSent() {
        when(sender.canSend()).thenReturn(false);

        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.resend(5L, "en")));
        verify(documents, never()).forDeliveredOrder(5L);
    }

    @Test
    void anOrderWithNothingToSendIsRefused() {
        when(sender.canSend()).thenReturn(true);
        when(documents.forDeliveredOrder(5L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.resend(5L, "vi")));
        verify(sender, never()).send(found);
    }

    @Test
    void aMailServerThatRefusesIsReportedNotHidden() {
        when(sender.canSend()).thenReturn(true);
        when(documents.forDeliveredOrder(5L)).thenReturn(Optional.of(found));
        when(sender.send(found)).thenReturn(false);

        assertEquals(HttpStatus.BAD_GATEWAY, statusOf(() -> service.resend(5L, "vi")));
        verify(auditLog, never()).record("gửi lại email phiếu", "ORD-1");
    }
}
