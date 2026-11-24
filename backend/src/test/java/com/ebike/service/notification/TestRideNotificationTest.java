package com.ebike.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.notification.Notification;
import com.ebike.model.entity.notification.NotificationType;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.notification.impl.NotificationCreationService;
import com.ebike.service.testride.TestRideMessages;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** V23: test-ride notices speak the language of the customer's profile and link to the customer's bookings. */
class TestRideNotificationTest {
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 11, 22, 9, 0);
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

    private TestRideNoticeEvent event(String kind) {
        return new TestRideNoticeEvent(7L, kind, "Kinetic X", "Quan 1", "187 Nguyen Thai Hoc", SLOT, null);
    }

    private void profile(String language, boolean notificationsOn) {
        UserPreference preference = new UserPreference();
        preference.setNotificationsEnabled(notificationsOn);
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(preference));
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        return captor.getValue();
    }

    @Test void customerWithoutAProfileGetsVietnamese() {
        service.handleTestRideNotice(event(TestRideMessages.REMINDER));
        Notification notification = saved();
        assertEquals("Nhắc lịch lái thử", notification.getTitle());
        assertTrue(notification.getMessage().contains("09:00 22/11/2026"), notification.getMessage());
    }

    @Test void erasedAccountGetsNone() {
        user.setAccountStatus("DELETED");
        service.handleTestRideNotice(event(TestRideMessages.CANCELLED));
        verify(notifications, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test void customerGetsAConfirmationWithTheTimeAndThePlace() {
        profile("vi", true);
        service.handleTestRideNotice(event(TestRideMessages.CONFIRMED));
        Notification notification = saved();
        assertEquals(NotificationType.TEST_RIDE, notification.getType());
        assertEquals("Lịch lái thử đã được xác nhận", notification.getTitle());
        assertTrue(notification.getMessage().contains("09:00 22/11/2026"), notification.getMessage());
        assertTrue(notification.getMessage().contains("Quan 1, 187 Nguyen Thai Hoc"), notification.getMessage());
        assertEquals("/customer/test-rides", notification.getTargetUrl());
    }

    @Test void customerWhoSwitchedNotificationsOffStillGetsNoticesOfTheirOwnBooking() {
        profile("vi", false);
        service.handleTestRideNotice(event(TestRideMessages.CANCELLED));
        assertEquals("Lịch lái thử đã bị hủy", saved().getTitle());
    }
}
