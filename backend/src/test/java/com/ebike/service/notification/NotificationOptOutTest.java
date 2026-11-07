package com.ebike.service.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.OrderCreatedEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.notification.impl.NotificationCreationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** V4: a customer who switched notifications off in the profile gets none. */
@ExtendWith(MockitoExtension.class)
class NotificationOptOutTest {
    @Mock NotificationRepository notifications;
    @Mock UserRepository users;
    @Mock UserPreferenceRepository preferences;
    @Mock NotificationStreamService stream;
    NotificationCreationService service;
    User user;

    @BeforeEach void setup() {
        service = new NotificationCreationService(notifications, users, stream, preferences);
        user = new User();
        user.setId(5L);
        when(users.findById(5L)).thenReturn(Optional.of(user));
    }

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(5L, 10L, 20L, "ORD-1", "PENDING");
    }

    @Test void aCustomerWhoOptedOutGetsNoNotification() {
        UserPreference preference = new UserPreference();
        preference.setNotificationsEnabled(false);
        when(preferences.findByUserId(5L)).thenReturn(Optional.of(preference));
        service.handleOrderCreated(event());
        verify(notifications, never()).save(any());
    }

    @Test void everyoneElseKeepsGettingThem() {
        when(preferences.findByUserId(5L)).thenReturn(Optional.empty()); // never changed the setting
        service.handleOrderCreated(event());
        verify(notifications).save(any());
    }
}
