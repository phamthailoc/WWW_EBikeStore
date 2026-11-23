package com.ebike.listener.notification;

import com.ebike.config.AsyncConfiguration;
import com.ebike.event.notification.OrderDeliveredEvent;
import com.ebike.service.document.DeliveryEmailSender;
import com.ebike.service.document.OrderDocumentService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * V27: emails the sales receipt and the warranty card once the order is committed as DELIVERED. The PDFs are made on their own bounded pool after the
 * commit (not the pool of the other emails, so a big restock cannot crowd them out), so neither a slow render nor a slow SMTP server holds up the staff
 * member's request, and a failure never undoes the hand-over. A full queue, a render failure or an SMTP failure is logged with the order id and the email
 * is not retried: the customer can still download both PDFs from the order page, and staff can from the Manager page.
 * The receipt is a transactional message, so it does not follow the "notifications" switch of the profile; an erased account gets nothing.
 */
@Component
public class OrderDeliveredEmailListener {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveredEmailListener.class);

    private final OrderDocumentService documents;
    private final DeliveryEmailSender sender;
    private final Executor executor;

    public OrderDeliveredEmailListener(
        OrderDocumentService documents,
        DeliveryEmailSender sender,
        @Qualifier(AsyncConfiguration.DOCUMENT_EXECUTOR) Executor executor
    ) {
        this.documents = documents;
        this.sender = sender;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        if (!sender.canSend()) {
            // mail is off or there is no SMTP host (the default local setup): do not render two PDFs for nothing
            log.debug("Hand-over email of order id {} skipped: mail is off or there is no SMTP host", event.orderId());
            return;
        }
        try {
            executor.execute(() -> send(event.orderId()));
        } catch (RejectedExecutionException exception) {
            log.error("The queue of hand-over emails is full: the email of order id {} was dropped and will not be retried", event.orderId());
        }
    }

    void send(Long orderId) {
        try {
            documents.forDeliveredOrder(orderId).ifPresent(found -> {
                if (!sender.send(found)) {
                    log.warn("The hand-over email of order {} was not sent (the order has no address, or the server refused it)", found.orderNumber());
                }
            });
        } catch (RuntimeException exception) {
            // nothing may escape into the pool's thread: the hand-over is already saved
            log.error("Could not prepare the hand-over email of order id {}", orderId, exception);
        }
    }
}
