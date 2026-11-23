package com.ebike.listener.notification;

import com.ebike.config.AsyncConfiguration;
import com.ebike.event.notification.OrderDeliveredEvent;
import com.ebike.service.document.DeliveryEmailSender;
import com.ebike.service.document.OrderDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * V27: emails the sales receipt and the warranty card once the order is committed as DELIVERED. The PDFs are made on the bounded mail pool after the
 * commit, so neither a slow render nor a slow SMTP server holds up the staff member's request, and a failure never undoes the hand-over.
 * The receipt is a transactional message, so it does not follow the "notifications" switch of the profile; an erased account gets nothing.
 */
@Component
public class OrderDeliveredEmailListener {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveredEmailListener.class);

    private final OrderDocumentService documents;
    private final DeliveryEmailSender sender;

    public OrderDeliveredEmailListener(OrderDocumentService documents, DeliveryEmailSender sender) {
        this.documents = documents;
        this.sender = sender;
    }

    @Async(AsyncConfiguration.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        try {
            documents.forDeliveredOrder(event.orderId()).ifPresent(sender::send);
        } catch (RuntimeException exception) {
            // nothing may escape into the mail pool's thread: the hand-over is already saved
            log.warn("Could not prepare the hand-over email of order {}: {}", event.orderId(), exception.getMessage());
        }
    }
}
