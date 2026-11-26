package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.notification.response.NotificationResponse;
import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.event.notification.OrderCreatedEvent;
import com.ebike.event.notification.OrderStatusChangedEvent;
import com.ebike.event.notification.PaymentStatusChangedEvent;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.event.notification.WarrantyClaimStatusChangedEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.notification.Notification;
import com.ebike.model.entity.notification.NotificationType;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.notification.NotificationStreamService;
import com.ebike.service.notification.impl.NotificationCreationService;
import com.ebike.service.notification.impl.NotificationServiceImpl;
import com.ebike.service.notification.impl.NotificationStreamServiceImpl;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationTest {

    private NotificationRepository repo;
    private UserRepository users;
    private NotificationStreamService stream;
    private UserPreferenceRepository prefs;
    private NotificationCreationService creation;
    private NotificationServiceImpl service;
    private User alice;

    @BeforeEach
    void setUp() {
        repo = mock(NotificationRepository.class);
        users = mock(UserRepository.class);
        stream = mock(NotificationStreamService.class);
        prefs = mock(UserPreferenceRepository.class);
        creation = new NotificationCreationService(repo, users, stream, prefs);
        service = new NotificationServiceImpl(repo, users, stream);
        alice = Fx.user(7, "alice");
        when(users.findById(7L)).thenReturn(Optional.of(alice));
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(prefs.findByUserId(7L)).thenReturn(Optional.empty());
        when(repo.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void optOut() {
        UserPreference p = new UserPreference();
        p.setNotificationsEnabled(false);
        when(prefs.findByUserId(7L)).thenReturn(Optional.of(p));
    }

    private Notification saved() {
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(cap.capture());
        return cap.getValue();
    }

    // ---------------- creation rules ----------------
    @Test
    void orderCreatedNotifiesAndPushesUnreadCount() {
        creation.handleOrderCreated(new OrderCreatedEvent(7L, 1L, 2L, "ORD-1", "PENDING"));
        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.ORDER_STATUS);
        assertThat(n.getMessage()).contains("ORD-1");
        assertThat(n.getTargetUrl()).isEqualTo("/customer/orders");
        assertThat(n.getReadAt()).isNull();
        verify(stream).sendUnreadCount(7L);
    }

    @Test
    void unknownUserGetsNothing() {
        when(users.findById(99L)).thenReturn(Optional.empty());
        creation.handleOrderCreated(new OrderCreatedEvent(99L, 1L, 2L, "ORD-1", "PENDING"));
        verify(repo, never()).save(any());
    }

    @Test
    void deletedAccountGetsNoNotificationOfAnyKind() {
        alice.setAccountStatus("DELETED");
        creation.handleOrderCreated(new OrderCreatedEvent(7L, 1L, 2L, "ORD-1", "PENDING"));
        creation.handleOrderStatusChanged(new OrderStatusChangedEvent(7L, 1L, 2L, "ORD-1", "CANCELLED"));
        creation.handleStockAlertTriggered(new StockAlertTriggeredEvent(7L, "Bike", "Red", "bike"));
        creation.handlePaymentStatusChanged(new PaymentStatusChangedEvent(7L, 1L, 2L, "ORD-1", "PAID", "VNPAY"));
        verify(repo, never()).save(any());
        verify(stream, never()).sendUnreadCount(anyLong());
    }

    @Test
    void optedOutCustomerGetsNoOrderOrPaymentNotices() {
        optOut();
        creation.handleOrderCreated(new OrderCreatedEvent(7L, 1L, 2L, "ORD-1", "PENDING"));
        creation.handleOrderStatusChanged(new OrderStatusChangedEvent(7L, 1L, 2L, "ORD-1", "CANCELLED"));
        creation.handlePaymentStatusChanged(new PaymentStatusChangedEvent(7L, 1L, 2L, "ORD-1", "FAILED", "VNPAY"));
        verify(repo, never()).save(any());
    }

    @Test
    void optedOutCustomerGetsNoWarrantyOrMaintenanceNotice() {
        optOut();
        creation.handleWarrantyClaimStatusChanged(new WarrantyClaimStatusChangedEvent(7L, "Bike", "F1", "DONE"));
        creation.handleMaintenanceDue(new MaintenanceDueEvent(7L, "Bike", "F1", "P6M"));
        verify(repo, never()).save(any());
    }

    @Test
    void stockAlertIgnoresOptOutBecauseCustomerAskedForIt() {
        optOut();
        creation.handleStockAlertTriggered(new StockAlertTriggeredEvent(7L, "Bike", "Red", "my bike"));
        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.STOCK_ALERT);
        assertThat(n.getTargetUrl()).isEqualTo("/product/my%20bike");
        assertThat(n.getOrderId()).isNull();
    }

    @Test
    void warrantyAndMaintenanceSkipLockedAndErasedAccounts() {
        alice.setActive(false);
        creation.handleWarrantyClaimStatusChanged(new WarrantyClaimStatusChangedEvent(7L, "Bike", "F1", "DONE"));
        creation.handleMaintenanceDue(new MaintenanceDueEvent(7L, "Bike", "F1", "P6M"));
        alice.setActive(true);
        alice.setAccountStatus("SUSPENDED");
        creation.handleMaintenanceDue(new MaintenanceDueEvent(7L, "Bike", "F1", "P6M"));
        verify(repo, never()).save(any());
    }

    @Test
    void maintenanceNoticeCarriesMilestoneInWordsAndWarrantyLink() {
        creation.handleMaintenanceDue(new MaintenanceDueEvent(7L, "Bike", "F1", "P6M"));
        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.MAINTENANCE_DUE);
        assertThat(n.getMessage()).contains("6 tháng").contains("F1");
        assertThat(n.getTargetUrl()).isEqualTo("/customer/warranty");
    }

    @Test
    void paymentNoticeSuccessAndFailureTypes() {
        creation.handlePaymentStatusChanged(new PaymentStatusChangedEvent(7L, 1L, 2L, "ORD-1", "PAID", "VNPAY"));
        assertThat(saved().getType()).isEqualTo(NotificationType.PAYMENT_SUCCESS);
    }

    @Test
    void paymentFailureType() {
        creation.handlePaymentStatusChanged(new PaymentStatusChangedEvent(7L, 1L, 2L, "ORD-1", "FAILED", "VNPAY"));
        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.PAYMENT_FAILED);
        assertThat(n.getMessage()).contains("ORD-1");
    }

    @Test
    void orderStatusIsTranslatedAndUnknownIsPassedThrough() {
        creation.handleOrderStatusChanged(new OrderStatusChangedEvent(7L, 1L, 2L, "ORD-1", "READY_FOR_PICKUP"));
        assertThat(saved().getMessage()).contains("chờ lấy xe");
    }

    // ---------------- NotificationServiceImpl ----------------
    private static Authentication auth(String name) {
        return new UsernamePasswordAuthenticationToken(name, null, List.of());
    }

    private Notification note(long id, User owner, LocalDateTime read) {
        Notification n = new Notification();
        ReflectionTestUtils.setField(n, "id", id);
        n.setUser(owner);
        n.setType(NotificationType.ORDER_STATUS);
        n.setTitle("t");
        n.setMessage("m");
        n.setReadAt(read);
        return n;
    }

    @Test
    void unauthenticatedIs401() {
        for (Authentication a : new Authentication[] {null, auth(""), auth(" ")}) {
            try {
                service.getNotifications(a);
                throw new AssertionError("expected 401");
            } catch (ResponseStatusException e) {
                assertThat(e.getStatusCode().value()).isEqualTo(401);
            }
        }
    }

    @Test
    void unknownPrincipalIs401() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(users.findByEmail("ghost")).thenReturn(Optional.empty());
        try {
            service.getUnreadCount(auth("ghost"));
            throw new AssertionError();
        } catch (ResponseStatusException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(401);
        }
    }

    @Test
    void markAsReadOnlyThroughOwnershipLookup() {
        when(repo.findByIdAndUserId(5L, 7L)).thenReturn(Optional.empty());
        try {
            service.markAsRead(5L, auth("alice"));
            throw new AssertionError();
        } catch (ResponseStatusException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
        verify(repo, never()).save(any());
        verify(repo, never()).findById(anyLong());
    }

    @Test
    void markAsReadSetsTimeOnceAndIsIdempotent() {
        LocalDateTime earlier = LocalDateTime.of(2025, 1, 1, 0, 0);
        Notification n = note(5, alice, earlier);
        when(repo.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(n));
        NotificationResponse r = service.markAsRead(5L, auth("alice"));
        assertThat(r.read()).isTrue();
        assertThat(r.readAt()).isEqualTo(earlier);
        Notification fresh = note(6, alice, null);
        when(repo.findByIdAndUserId(6L, 7L)).thenReturn(Optional.of(fresh));
        assertThat(service.markAsRead(6L, auth("alice")).readAt()).isNotNull();
        verify(stream, org.mockito.Mockito.times(2)).sendUnreadCount(7L);
    }

    @Test
    void markAllAsReadOnlyTouchesOwnUnread() {
        Notification a = note(1, alice, null);
        Notification b = note(2, alice, null);
        when(repo.findByUserIdAndReadAtIsNull(7L)).thenReturn(List.of(a, b));
        when(repo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        List<NotificationResponse> out = service.markAllAsRead(auth("alice"));
        assertThat(out).hasSize(2).allSatisfy(r -> assertThat(r.read()).isTrue());
        assertThat(a.getReadAt()).isEqualTo(b.getReadAt());
        verify(repo, never()).findByUserIdAndReadAtIsNull(8L);
    }

    @Test
    void emailLoginNameResolvesToo() {
        when(users.findByUsername("alice@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        assertThat(service.getCurrentUserId(auth("alice@example.com"))).isEqualTo(7L);
    }

    @Test
    void unreadCountAndListUseOwnId() {
        when(repo.countByUserIdAndReadAtIsNull(7L)).thenReturn(4L);
        assertThat(service.getUnreadCount(auth("alice")).unreadCount()).isEqualTo(4L);
        when(repo.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(note(1, alice, null)));
        assertThat(service.getNotifications(auth("alice"))).hasSize(1);
    }

    // ---------------- stream ----------------
    @Test
    void streamSendsNothingWithoutSubscribersAndQueriesOnlyWhenNeeded() {
        NotificationStreamServiceImpl s = new NotificationStreamServiceImpl(repo);
        s.sendUnreadCount(7L);
        verify(repo, never()).countByUserIdAndReadAtIsNull(anyLong());
    }

    @Test
    void subscribeRegistersEmitterPerUserAndCountsOncePerPush() {
        NotificationStreamServiceImpl s = new NotificationStreamServiceImpl(repo);
        when(repo.countByUserIdAndReadAtIsNull(7L)).thenReturn(3L);
        SseEmitter e1 = s.subscribe(7L);
        SseEmitter e2 = s.subscribe(7L);
        assertThat(e1).isNotSameAs(e2);
        assertThat(e1.getTimeout()).isEqualTo(30L * 60 * 1000);
        s.subscribe(8L);
        Map<?, ?> map = (Map<?, ?>) ReflectionTestUtils.getField(s, "emittersByUser");
        assertThat(map.containsKey(7L) && map.containsKey(8L)).isTrue();
        assertThat((List<?>) map.get(7L)).hasSize(2);
    }

    @Test
    void deadEmitterIsRemovedAndEmptyUserEntryDropped() throws IOException {
        NotificationStreamServiceImpl s = new NotificationStreamServiceImpl(repo);
        when(repo.countByUserIdAndReadAtIsNull(7L)).thenReturn(1L);
        SseEmitter dead = mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new IOException("closed")).when(dead).send(any(SseEmitter.SseEventBuilder.class));
        @SuppressWarnings("unchecked")
        Map<Long, List<SseEmitter>> map = (Map<Long, List<SseEmitter>>) ReflectionTestUtils.getField(s, "emittersByUser");
        map.put(7L, new java.util.concurrent.CopyOnWriteArrayList<>(List.of(dead)));
        s.sendUnreadCount(7L);
        assertThat(map).doesNotContainKey(7L);
    }

    @Test
    void emitterThatThrowsIllegalStateIsAlsoRemoved() throws IOException {
        NotificationStreamServiceImpl s = new NotificationStreamServiceImpl(repo);
        SseEmitter completed = mock(SseEmitter.class);
        SseEmitter live = mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("done")).when(completed).send(any(SseEmitter.SseEventBuilder.class));
        @SuppressWarnings("unchecked")
        Map<Long, List<SseEmitter>> map = (Map<Long, List<SseEmitter>>) ReflectionTestUtils.getField(s, "emittersByUser");
        map.put(7L, new java.util.concurrent.CopyOnWriteArrayList<>(List.of(completed, live)));
        s.sendUnreadCount(7L);
        assertThat(map.get(7L)).containsExactly(live);
        verify(live).send(any(SseEmitter.SseEventBuilder.class));
    }
}
