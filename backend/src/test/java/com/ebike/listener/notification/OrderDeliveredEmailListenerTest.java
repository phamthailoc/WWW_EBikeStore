package com.ebike.listener.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.OrderDeliveredEvent;
import com.ebike.service.document.DeliveryEmailSender;
import com.ebike.service.document.OrderDocumentService;
import com.ebike.service.document.PdfFile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** V27: the hand-over email is sent from the committed order, and nothing that goes wrong reaches the mail pool's thread. */
class OrderDeliveredEmailListenerTest {

    private final OrderDocumentService documents = mock(OrderDocumentService.class);
    private final DeliveryEmailSender sender = mock(DeliveryEmailSender.class);
    private final OrderDeliveredEmailListener listener = new OrderDeliveredEmailListener(documents, sender, Runnable::run);

    @org.junit.jupiter.api.BeforeEach
    void mailIsOn() {
        when(sender.canSend()).thenReturn(true);
    }

    @Test
    void nothingIsRenderedWhileMailIsOff() {
        when(sender.canSend()).thenReturn(false);

        listener.onOrderDelivered(new OrderDeliveredEvent(5L));

        verify(documents, never()).forDeliveredOrder(any());
    }

    @Test
    void theDocumentsOfTheOrderAreSentToTheCustomer() {
        var found = new OrderDocumentService.DeliveryDocuments("an@example.com", "An", "ORD-1", "vi", List.of(new PdfFile("receipt-ORD-1.pdf", new byte[] {1})));
        when(documents.forDeliveredOrder(5L)).thenReturn(Optional.of(found));

        listener.onOrderDelivered(new OrderDeliveredEvent(5L));

        verify(sender).send(found);
    }

    @Test
    void nothingIsSentWhenThereIsNothingToSend() {
        when(documents.forDeliveredOrder(5L)).thenReturn(Optional.empty());

        listener.onOrderDelivered(new OrderDeliveredEvent(5L));

        verify(sender, never()).send(any());
    }

    @Test
    void aFailureWhileMakingThePdfDoesNotEscape() {
        when(documents.forDeliveredOrder(5L)).thenThrow(new IllegalStateException("render"));

        listener.onOrderDelivered(new OrderDeliveredEvent(5L));

        verify(sender, never()).send(any());
    }

    @Test
    void aFullQueueDoesNotEscapeAndNothingIsSent() {
        var listener = new OrderDeliveredEmailListener(documents, sender, task -> {
            throw new java.util.concurrent.RejectedExecutionException("full");
        });

        listener.onOrderDelivered(new OrderDeliveredEvent(5L));

        verify(documents, never()).forDeliveredOrder(any());
        verify(sender, never()).send(any());
    }
}
