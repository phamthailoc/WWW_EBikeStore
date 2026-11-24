package com.ebike.listener.notification;

import com.ebike.config.AsyncConfiguration;
import com.ebike.event.notification.InstallmentNoticeEvent;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.installment.InstallmentEmailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** V24: sends the instalment decision email once the decision has been saved. */
@Component
public class InstallmentEmailListener {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final InstallmentEmailSender emailSender;

    public InstallmentEmailListener(
        UserRepository userRepository,
        UserPreferenceRepository userPreferenceRepository,
        InstallmentEmailSender emailSender
    ) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.emailSender = emailSender;
    }

    // Sent from the bounded mail thread pool after the commit, so a slow SMTP server never holds up the staff's request.
    @Async(AsyncConfiguration.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInstallmentNotice(InstallmentNoticeEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            if ("DELETED".equals(user.getAccountStatus())) {
                return;
            }
            // Like the in-app notice, this email is about an application the customer made: the profile's notification switch does not stop it.
            emailSender.send(user.getEmail(), event.kind(), event.orderId(), event.orderNumber(), event.downPayment(), event.monthlyPayment(),
                event.termMonths(), event.payBefore(), event.note());
        });
    }
}
