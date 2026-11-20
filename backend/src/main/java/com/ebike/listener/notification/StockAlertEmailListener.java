package com.ebike.listener.notification;

import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.stock.StockAlertEmailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** V22: sends the back-in-stock email once the stock update has been saved. */
@Component
public class StockAlertEmailListener {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final StockAlertEmailSender emailSender;

    public StockAlertEmailListener(
        UserRepository userRepository,
        UserPreferenceRepository userPreferenceRepository,
        StockAlertEmailSender emailSender
    ) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockAlertTriggered(StockAlertTriggeredEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            if ("DELETED".equals(user.getAccountStatus())) {
                return;
            }
            boolean optedOut = userPreferenceRepository.findByUserId(user.getId())
                .map(preference -> Boolean.FALSE.equals(preference.getNotificationsEnabled()))
                .orElse(false);
            if (!optedOut) {
                emailSender.send(user.getEmail(), event.productName(), event.colorName());
            }
        });
    }
}
