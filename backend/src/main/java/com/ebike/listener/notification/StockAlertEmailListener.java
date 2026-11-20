package com.ebike.listener.notification;

import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.repository.auth.UserRepository;
import com.ebike.service.stock.StockAlertEmailSender;
import com.ebike.service.stock.StockAlertRules;
import com.ebike.config.AsyncConfiguration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** V22: sends the back-in-stock email once the stock update has been saved. */
@Component
public class StockAlertEmailListener {

    private final UserRepository userRepository;
    private final StockAlertEmailSender emailSender;

    public StockAlertEmailListener(
        UserRepository userRepository,
        StockAlertEmailSender emailSender
    ) {
        this.userRepository = userRepository;
        this.emailSender = emailSender;
    }

    // Sent from the bounded mail thread pool after the commit, so a slow SMTP server never holds up the stock update.
    @Async(AsyncConfiguration.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockAlertTriggered(StockAlertTriggeredEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            if (!StockAlertRules.canReceive(user.getAccountStatus(), user.getActive())) {
                return;
            }
            // The profile's notification switch is not checked: the customer asked for this alert on purpose.
            emailSender.send(user.getId(), user.getEmail(), event.productName(), event.colorName(), event.productSlug(), event.language());
        });
    }
}
