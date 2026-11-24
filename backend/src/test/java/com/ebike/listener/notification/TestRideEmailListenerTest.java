package com.ebike.listener.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.testride.TestRideEmailSender;
import com.ebike.service.testride.TestRideMessages;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** V23: the test-ride email goes to working accounts that want notifications, in their language, and only for the kinds that are emailed. */
class TestRideEmailListenerTest {

    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 11, 22, 9, 0);
    UserRepository users = mock(UserRepository.class);
    UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    TestRideEmailSender sender = mock(TestRideEmailSender.class);
    TestRideEmailListener listener = new TestRideEmailListener(users, preferences, sender);
    User user;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(7L);
        user.setEmail("an@example.test");
        user.setAccountStatus("ACTIVE");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(preferences.findByUserId(7L)).thenReturn(Optional.empty());
    }

    private TestRideNoticeEvent event(String kind) {
        return new TestRideNoticeEvent(7L, kind, "Kinetic X", "Quan 1", "187 Nguyen Thai Hoc", SLOT, "note");
    }

    @Test
    void aCustomerWithNoSavedProfileGetsTheVietnameseEmail() {
        listener.onTestRideNotice(event(TestRideMessages.CONFIRMED));
        verify(sender).send("an@example.test", TestRideMessages.CONFIRMED, "Kinetic X", "Quan 1", "187 Nguyen Thai Hoc", SLOT, "note");
    }

    @Test
    void aCustomerWhoSwitchedNotificationsOffStillGetsTheEmailOfTheirOwnBooking() {
        UserPreference off = new UserPreference();
        off.setNotificationsEnabled(false);
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(off));
        listener.onTestRideNotice(event(TestRideMessages.CONFIRMED));
        verify(sender).send("an@example.test", TestRideMessages.CONFIRMED, "Kinetic X", "Quan 1", "187 Nguyen Thai Hoc", SLOT, "note");
    }

    @Test
    void anErasedAccountGetsNothing() {
        user.setAccountStatus("DELETED");
        listener.onTestRideNotice(event(TestRideMessages.CONFIRMED));
        verify(sender, never()).send(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void kindsThatAreOnlyInAppNeverReachTheSender() {
        listener.onTestRideNotice(event(TestRideMessages.NO_SHOW));
        listener.onTestRideNotice(event(TestRideMessages.EXPIRED));
        verify(sender, never()).send(any(), any(), any(), any(), any(), any(), any());
    }
}
