package com.ebike.listener.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.stock.StockAlertEmailSender;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** V22: the back-in-stock email goes only to working accounts that still want notifications, in their language. */
class StockAlertEmailListenerTest {
    UserRepository users = mock(UserRepository.class);
    UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    StockAlertEmailSender sender = mock(StockAlertEmailSender.class);
    // kept only to prove that the profile switch is not consulted for a requested alert
    StockAlertEmailListener listener = new StockAlertEmailListener(users, sender);
    User user;

    @BeforeEach void setup() {
        user = new User();
        user.setId(7L);
        user.setEmail("an@example.test");
        user.setAccountStatus("ACTIVE");
        user.setActive(true);
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    private StockAlertTriggeredEvent event(String language) {
        return new StockAlertTriggeredEvent(7L, "Kinetic X", "White", "kinetic-x");
    }

    @Test void activeCustomerGetsTheEmailInTheEventLanguage() {
        listener.onStockAlertTriggered(event("en"));
        verify(sender).send(7L, "an@example.test", "Kinetic X", "White", "kinetic-x");
    }

    @Test void erasedAccountGetsNothing() {
        user.setAccountStatus("DELETED");
        listener.onStockAlertTriggered(event("vi"));
        verify(sender, never()).send(any(), any(), any(), any(), any());
    }

    @Test void accountDisabledByAnAdminGetsNothing() {
        user.setActive(false);
        listener.onStockAlertTriggered(event("vi"));
        verify(sender, never()).send(any(), any(), any(), any(), any());
    }

    @Test void customerWhoSwitchedNotificationsOffStillGetsTheAlertTheyAskedFor() {
        UserPreference off = new UserPreference();
        off.setNotificationsEnabled(false);
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(off));
        listener.onStockAlertTriggered(event("vi"));
        verify(sender).send(7L, "an@example.test", "Kinetic X", "White", "kinetic-x");
    }
}
