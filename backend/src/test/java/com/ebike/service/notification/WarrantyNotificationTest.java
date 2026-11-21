package com.ebike.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.event.notification.WarrantyClaimStatusChangedEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.notification.Notification;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.notification.impl.NotificationCreationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** V20: warranty and service-due notices speak the language of the customer's profile and link to the warranty book. */
class WarrantyNotificationTest {
    NotificationRepository notifications = mock(NotificationRepository.class);
    UserRepository users = mock(UserRepository.class);
    UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    NotificationCreationService service = new NotificationCreationService(notifications, users, mock(NotificationStreamService.class), preferences);
    User user = new User();

    @BeforeEach void setup() {
        user.setId(7L);
        user.setAccountStatus("ACTIVE");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(preferences.findByUserId(7L)).thenReturn(Optional.empty());
    }

    private void language(String code, boolean notificationsOn) {
        UserPreference preference = new UserPreference();
        preference.setLanguage(code);
        preference.setNotificationsEnabled(notificationsOn);
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(preference));
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        return captor.getValue();
    }

    @Test void englishCustomerGetsAnEnglishClaimNotice() {
        language("en", true);
        service.handleWarrantyClaimStatusChanged(new WarrantyClaimStatusChangedEvent(7L, "Kinetic X", "FRAME12345", "RECEIVED"));
        Notification notification = saved();
        assertEquals("Warranty request update", notification.getTitle());
        assertTrue(notification.getMessage().contains("was received by the store"));
        assertEquals("/customer/warranty", notification.getTargetUrl());
    }

    @Test void customerWithoutAProfileGetsVietnamese() {
        service.handleWarrantyClaimStatusChanged(new WarrantyClaimStatusChangedEvent(7L, "Kinetic X", "FRAME12345", "DONE"));
        Notification notification = saved();
        assertEquals("Cập nhật yêu cầu bảo hành", notification.getTitle());
        assertTrue(notification.getMessage().contains("đã hoàn tất"));
    }

    @Test void englishCustomerGetsAnEnglishServiceNotice() {
        language("en", true);
        service.handleMaintenanceDue(new MaintenanceDueEvent(7L, "Kinetic X", "FRAME12345", "P6M"));
        Notification notification = saved();
        assertEquals("Service due", notification.getTitle());
        assertTrue(notification.getMessage().contains("6 months after delivery"));
        assertEquals("/customer/warranty", notification.getTargetUrl());
    }

    @Test void customerWhoSwitchedNotificationsOffGetsNone() {
        language("en", false);
        service.handleMaintenanceDue(new MaintenanceDueEvent(7L, "Kinetic X", "FRAME12345", "P6M"));
        verify(notifications, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test void anErasedAccountGetsNone() {
        user.setAccountStatus("DELETED");
        service.handleWarrantyClaimStatusChanged(new WarrantyClaimStatusChangedEvent(7L, "Kinetic X", "FRAME12345", "DONE"));
        verify(notifications, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
