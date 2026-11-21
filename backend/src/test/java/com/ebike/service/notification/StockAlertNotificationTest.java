package com.ebike.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.notification.Notification;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.notification.impl.NotificationCreationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** V22: the in-app back-in-stock notice links to the bike page and speaks the customer's language. */
class StockAlertNotificationTest {
    NotificationRepository notifications = mock(NotificationRepository.class);
    UserRepository users = mock(UserRepository.class);
    UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    NotificationCreationService service = new NotificationCreationService(notifications, users, mock(NotificationStreamService.class), preferences);

    @BeforeEach void setup() {
        User user = new User();
        user.setId(7L);
        user.setAccountStatus("ACTIVE");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(preferences.findByUserId(7L)).thenReturn(Optional.empty());
    }

    private Notification saved(String language) {
        service.handleStockAlertTriggered(new StockAlertTriggeredEvent(7L, "Kinetic X", "White", "kinetic-x", language));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        return captor.getValue();
    }

    @Test void englishCustomerGetsEnglishTextAndTheBikeLink() {
        Notification notification = saved("en");
        assertEquals("/product/kinetic-x", notification.getTargetUrl());
        assertEquals("Back in stock", notification.getTitle());
        assertTrue(notification.getMessage().contains("Kinetic X"));
    }

    @Test void customerWhoSwitchedNotificationsOffStillGetsTheAlertTheyAskedFor() {
        com.ebike.model.entity.user.UserPreference off = new com.ebike.model.entity.user.UserPreference();
        off.setNotificationsEnabled(false);
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(off));
        assertEquals("Back in stock", saved("en").getTitle());
    }

    @Test void aSlugIsEncodedAndAMissingSlugGivesNoLink() {
        service.handleStockAlertTriggered(new StockAlertTriggeredEvent(7L, "Kinetic X", "White", "xe điện/1", "en"));
        service.handleStockAlertTriggered(new StockAlertTriggeredEvent(7L, "Kinetic X", "White", null, "en"));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("/product/xe%20%C4%91i%E1%BB%87n%2F1", captor.getAllValues().get(0).getTargetUrl());
        org.junit.jupiter.api.Assertions.assertNull(captor.getAllValues().get(1).getTargetUrl());
    }

    @Test void vietnameseCustomerGetsVietnameseText() {
        Notification notification = saved("vi");
        assertEquals("Xe đã có hàng lại", notification.getTitle());
        assertEquals("/product/kinetic-x", notification.getTargetUrl());
    }
}
