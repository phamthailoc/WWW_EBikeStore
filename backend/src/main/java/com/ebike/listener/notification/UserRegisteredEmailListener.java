package com.ebike.listener.notification;

import com.ebike.config.AsyncConfiguration;
import com.ebike.event.notification.UserRegisteredEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.service.auth.AccountEmailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the welcome email from the bounded mail pool once the new account is committed. */
@Component
public class UserRegisteredEmailListener {

    private final UserRepository userRepository;
    private final AccountEmailSender emailSender;

    public UserRegisteredEmailListener(UserRepository userRepository, AccountEmailSender emailSender) {
        this.userRepository = userRepository;
        this.emailSender = emailSender;
    }

    @Async(AsyncConfiguration.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        if (!emailSender.canSend()) {
            return; // mail is off or there is no SMTP host (the default local setup): no query for nothing
        }
        userRepository.findById(event.userId()).ifPresent(this::send);
    }

    private void send(User user) {
        if ("DELETED".equals(user.getAccountStatus())) {
            return;
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        emailSender.sendWelcome(user.getEmail(), (first + " " + last).trim(), user.getUsername());
    }
}
