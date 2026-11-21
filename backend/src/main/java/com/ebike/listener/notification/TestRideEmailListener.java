package com.ebike.listener.notification;

import com.ebike.config.AsyncConfiguration;
import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.testride.TestRideEmailSender;
import com.ebike.service.testride.TestRideMessages;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** V23: sends the test-ride email once the booking row has been saved. */
@Component
public class TestRideEmailListener {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final TestRideEmailSender emailSender;

    public TestRideEmailListener(
        UserRepository userRepository,
        UserPreferenceRepository userPreferenceRepository,
        TestRideEmailSender emailSender
    ) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.emailSender = emailSender;
    }

    // Sent from the bounded mail thread pool after the commit, so a slow SMTP server never holds up the staff's request or the job.
    @Async(AsyncConfiguration.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTestRideNotice(TestRideNoticeEvent event) {
        if (!TestRideMessages.sendsEmail(event.kind())) {
            return;
        }
        userRepository.findById(event.userId()).ifPresent(user -> {
            if ("DELETED".equals(user.getAccountStatus())) {
                return;
            }
            // Like the in-app notice, this email is about a booking the customer made: the profile's notification switch does not stop it.
            String language = userPreferenceRepository.findByUserId(user.getId()).map(saved -> saved.getLanguage()).orElse("vi");
            emailSender.send(user.getEmail(), event.kind(), event.productName(), event.showroomName(), event.showroomAddress(),
                event.slotStart(), event.note(), language);
        });
    }
}
