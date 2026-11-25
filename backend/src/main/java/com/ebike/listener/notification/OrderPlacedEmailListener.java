package com.ebike.listener.notification;

import com.ebike.config.AsyncConfiguration;
import com.ebike.event.notification.OrderPlacedEvent;
import com.ebike.service.order.impl.OrderPlacedEmailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the order confirmation after the checkout transaction has committed. */
@Component
public class OrderPlacedEmailListener {

    private final OrderPlacedEmailService emailService;

    public OrderPlacedEmailListener(OrderPlacedEmailService emailService) {
        this.emailService = emailService;
    }

    @Async(AsyncConfiguration.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        // the order is read inside a short read-only transaction; the SMTP call happens after it, so no pooled connection waits on the mail server
        emailService.compose(event.orderId()).ifPresent(emailService::deliver);
    }
}
