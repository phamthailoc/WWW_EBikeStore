package com.ebike.listener.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.warranty.MaintenanceEmailSender;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** V20: the maintenance email goes only to an account that is alive and has notifications on, in its saved language. */
class MaintenanceEmailListenerTest {

    private UserRepository users;
    private UserPreferenceRepository preferences;
    private MaintenanceEmailSender sender;
    private MaintenanceEmailListener listener;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        preferences = mock(UserPreferenceRepository.class);
        sender = mock(MaintenanceEmailSender.class);
        listener = new MaintenanceEmailListener(users, preferences, sender);
        user = new User();
        user.setId(4L);
        user.setEmail("an@example.com");
        when(users.findById(4L)).thenReturn(Optional.of(user));
        when(preferences.findByUserId(4L)).thenReturn(Optional.empty());
    }

    private static MaintenanceDueEvent event() {
        return new MaintenanceDueEvent(4L, "Test bike", "FRAME1", "P6M");
    }

    private static UserPreference preference(String language, boolean notifications) {
        UserPreference preference = new UserPreference();
        preference.setLanguage(language);
        preference.setNotificationsEnabled(notifications);
        return preference;
    }

    @Test
    void anAccountWithoutAPreferenceRowGetsTheEmailInVietnamese() {
        listener.onMaintenanceDue(event());

        verify(sender).send("an@example.com", "Test bike", "FRAME1", "P6M", "vi");
    }

    @Test
    void theEmailUsesTheLanguageSavedOnTheAccount() {
        when(preferences.findByUserId(4L)).thenReturn(Optional.of(preference("en", true)));

        listener.onMaintenanceDue(event());

        verify(sender).send("an@example.com", "Test bike", "FRAME1", "P6M", "en");
    }

    @Test
    void turnedOffNotificationsMeanNoEmail() {
        when(preferences.findByUserId(4L)).thenReturn(Optional.of(preference("vi", false)));

        listener.onMaintenanceDue(event());

        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void anErasedAccountGetsNoEmail() {
        user.setAccountStatus("DELETED");

        listener.onMaintenanceDue(event());

        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void anUnknownAccountIsIgnored() {
        when(users.findById(any())).thenReturn(Optional.empty());

        listener.onMaintenanceDue(event());

        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
