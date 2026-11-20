package com.ebike.listener.notification;

import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.warranty.MaintenanceEmailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** V20: sends the maintenance reminder email once the reminder rows have been saved. */
@Component
public class MaintenanceEmailListener {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final MaintenanceEmailSender emailSender;

    public MaintenanceEmailListener(
        UserRepository userRepository,
        UserPreferenceRepository userPreferenceRepository,
        MaintenanceEmailSender emailSender
    ) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMaintenanceDue(MaintenanceDueEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            if ("DELETED".equals(user.getAccountStatus())) {
                return;
            }
            boolean optedOut = userPreferenceRepository.findByUserId(user.getId())
                .map(preference -> Boolean.FALSE.equals(preference.getNotificationsEnabled()))
                .orElse(false);
            if (!optedOut) {
                emailSender.send(user.getEmail(), event.productName(), event.frameNumber(), event.milestone());
            }
        });
    }
}
