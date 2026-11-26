package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.InstallmentNoticeEvent;
import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.event.notification.OrderCreatedEvent;
import com.ebike.event.notification.OrderDeliveredEvent;
import com.ebike.event.notification.OrderStatusChangedEvent;
import com.ebike.event.notification.PaymentStatusChangedEvent;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.listener.notification.InstallmentEmailListener;
import com.ebike.listener.notification.MaintenanceEmailListener;
import com.ebike.listener.notification.OrderDeliveredEmailListener;
import com.ebike.listener.notification.OrderNotificationJpaListener;
import com.ebike.listener.notification.PaymentNotificationJpaListener;
import com.ebike.listener.notification.StockAlertEmailListener;
import com.ebike.listener.notification.TestRideEmailListener;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.document.DeliveryEmailSender;
import com.ebike.service.document.OrderDocumentService;
import com.ebike.service.installment.InstallmentEmailSender;
import com.ebike.service.stock.StockAlertEmailSender;
import com.ebike.service.testride.TestRideEmailSender;
import com.ebike.service.warranty.MaintenanceEmailSender;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class ListenerTest {

    private List<Object> events;
    private UserRepository users;
    private UserPreferenceRepository prefs;
    private User alice;

    @BeforeEach
    void setUp() {
        events = Fx.captureEvents();
        users = mock(UserRepository.class);
        prefs = mock(UserPreferenceRepository.class);
        alice = Fx.user(7, "alice");
        when(users.findById(7L)).thenReturn(Optional.of(alice));
        when(prefs.findByUserId(7L)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        Fx.release();
    }

    private Order order(OrderStatus now, OrderStatus before) {
        Order o = new Order();
        o.setId(5L);
        o.setOrderNumber("ORD-5");
        o.setUser(alice);
        o.setStatus(now);
        ReflectionTestUtils.setField(o, "previousStatusForNotification", before);
        return o;
    }

    // ---------------- Order JPA listener ----------------
    @Test
    void orderCreatedPublishedOnPersist() {
        new OrderNotificationJpaListener().afterCreate(order(OrderStatus.PENDING, null));
        assertThat(events).hasSize(1).first().isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    void guestOrderCreatedPublishesNothing() {
        Order o = order(OrderStatus.PENDING, null);
        o.setUser(null);
        new OrderNotificationJpaListener().afterCreate(o);
        new OrderNotificationJpaListener().afterCreate(null);
        assertThat(events).isEmpty();
    }

    @Test
    void statusChangePublishesOnceAndMarksAnnounced() {
        Order o = order(OrderStatus.CONFIRMED, OrderStatus.PENDING);
        OrderNotificationJpaListener l = new OrderNotificationJpaListener();
        l.afterUpdate(o);
        assertThat(events).hasSize(1).first().isInstanceOf(OrderStatusChangedEvent.class);
        assertThat(((OrderStatusChangedEvent) events.get(0)).status()).isEqualTo("CONFIRMED");
        l.afterUpdate(o); // a second flush of the same state must not announce again
        assertThat(events).hasSize(1);
    }

    @Test
    void noChangeOrNoBaselineMeansSilence() {
        OrderNotificationJpaListener l = new OrderNotificationJpaListener();
        l.afterUpdate(order(OrderStatus.PENDING, OrderStatus.PENDING));
        l.afterUpdate(order(OrderStatus.PENDING, null));
        l.afterUpdate(null);
        assertThat(events).isEmpty();
    }

    @Test
    void deliveredPublishesDocumentEventEvenForGuestButNoStatusNoticeForGuest() {
        Order o = order(OrderStatus.DELIVERED, OrderStatus.SHIPPED);
        o.setUser(null);
        new OrderNotificationJpaListener().afterUpdate(o);
        assertThat(events).hasSize(1).first().isInstanceOf(OrderDeliveredEvent.class);
    }

    @Test
    void deliveredForCustomerPublishesBoth() {
        new OrderNotificationJpaListener().afterUpdate(order(OrderStatus.DELIVERED, OrderStatus.SHIPPED));
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(OrderDeliveredEvent.class);
        assertThat(events.get(1)).isInstanceOf(OrderStatusChangedEvent.class);
    }

    @Test
    void suppressedStatusNoticeStillMarksAnnounced() {
        Order o = order(OrderStatus.CANCELLED, OrderStatus.PENDING);
        o.setSuppressStatusNotice(true);
        new OrderNotificationJpaListener().afterUpdate(o);
        assertThat(events).isEmpty();
        assertThat(o.getPreviousStatusForNotification()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ---------------- Payment JPA listener ----------------
    private Payment payment(PaymentStatus now, PaymentStatus before) {
        Payment p = new Payment();
        p.setOrder(order(OrderStatus.PENDING, OrderStatus.PENDING));
        p.setPaymentStatus(now);
        p.setPaymentMethod(PaymentMethod.INSTALLMENT);
        ReflectionTestUtils.setField(p, "previousPaymentStatusForNotification", before);
        return p;
    }

    @Test
    void paymentOnlyPaidOrFailedAreAnnounced() {
        PaymentNotificationJpaListener l = new PaymentNotificationJpaListener();
        l.afterUpdate(payment(PaymentStatus.PAID, PaymentStatus.PENDING));
        l.afterUpdate(payment(PaymentStatus.FAILED, PaymentStatus.PENDING));
        assertThat(events).hasSize(2);
        l.afterUpdate(payment(PaymentStatus.REFUNDED, PaymentStatus.PAID));
        l.afterUpdate(payment(PaymentStatus.CANCELLED, PaymentStatus.PENDING));
        l.afterUpdate(payment(PaymentStatus.AUTHORIZED, PaymentStatus.PENDING));
        l.afterUpdate(payment(PaymentStatus.PAID, PaymentStatus.PAID));
        l.afterUpdate(payment(PaymentStatus.PAID, null));
        l.afterUpdate(null);
        assertThat(events).hasSize(2);
        PaymentStatusChangedEvent e = (PaymentStatusChangedEvent) events.get(0);
        assertThat(e.paymentMethod()).isEqualTo("INSTALLMENT");
    }

    @Test
    void paymentOfGuestOrderIsSilent() {
        Payment p = payment(PaymentStatus.PAID, PaymentStatus.PENDING);
        p.getOrder().setUser(null);
        new PaymentNotificationJpaListener().afterUpdate(p);
        assertThat(events).isEmpty();
    }

    // ---------------- stock alert email listener ----------------
    @Test
    void stockAlertEmailOnlyToWorkingAccountsAndIgnoresOptOut() {
        StockAlertEmailSender sender = mock(StockAlertEmailSender.class);
        UserPreference off = new UserPreference();
        off.setNotificationsEnabled(false);
        StockAlertEmailListener l = new StockAlertEmailListener(users, sender);
        StockAlertTriggeredEvent ev = new StockAlertTriggeredEvent(7L, "Bike", "Red", "bike");
        l.onStockAlertTriggered(ev);
        verify(sender).send(7L, "alice@example.com", "Bike", "Red", "bike");
        alice.setAccountStatus("DELETED");
        l.onStockAlertTriggered(ev);
        alice.setAccountStatus("ACTIVE");
        alice.setActive(false);
        l.onStockAlertTriggered(ev);
        when(users.findById(8L)).thenReturn(Optional.empty());
        l.onStockAlertTriggered(new StockAlertTriggeredEvent(8L, "B", "R", "b"));
        verify(sender, org.mockito.Mockito.times(1)).send(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    // ---------------- maintenance email listener ----------------
    @Test
    void maintenanceEmailRespectsOptOutAndAccountState() {
        MaintenanceEmailSender sender = mock(MaintenanceEmailSender.class);
        MaintenanceEmailListener l = new MaintenanceEmailListener(users, prefs, sender);
        MaintenanceDueEvent ev = new MaintenanceDueEvent(7L, "Bike", "F1", "P6M");
        l.onMaintenanceDue(ev);
        verify(sender).send("alice@example.com", "Bike", "F1", "P6M");
        UserPreference off = new UserPreference();
        off.setNotificationsEnabled(false);
        when(prefs.findByUserId(7L)).thenReturn(Optional.of(off));
        l.onMaintenanceDue(ev);
        UserPreference on = new UserPreference();
        on.setNotificationsEnabled(null);
        when(prefs.findByUserId(7L)).thenReturn(Optional.of(on));
        l.onMaintenanceDue(ev);
        verify(sender, org.mockito.Mockito.times(2)).send(anyString(), anyString(), anyString(), anyString());
        alice.setAccountStatus("DELETED");
        l.onMaintenanceDue(ev);
        alice.setAccountStatus("ACTIVE");
        alice.setActive(false);
        l.onMaintenanceDue(ev);
        verify(sender, org.mockito.Mockito.times(2)).send(anyString(), anyString(), anyString(), anyString());
    }

    // ---------------- test-ride / instalment email listeners ----------------
    @Test
    void testRideEmailSkipsInAppOnlyKindsAndErasedAccounts() {
        TestRideEmailSender sender = mock(TestRideEmailSender.class);
        TestRideEmailListener l = new TestRideEmailListener(users, prefs, sender);
        l.onTestRideNotice(new TestRideNoticeEvent(7L, "NO_SUCH_KIND", "B", "S", "A", null, null));
        verify(sender, never()).send(any(), any(), any(), any(), any(), any(), any());
        alice.setAccountStatus("DELETED");
        l.onTestRideNotice(new TestRideNoticeEvent(7L, com.ebike.service.testride.TestRideMessages.CONFIRMED, "B", "S", "A", null, null));
        verify(sender, never()).send(any(), any(), any(), any(), any(), any(), any());
        alice.setAccountStatus("ACTIVE");
        l.onTestRideNotice(new TestRideNoticeEvent(7L, com.ebike.service.testride.TestRideMessages.CONFIRMED, "B", "S", "A", null, null));
        verify(sender).send(eq("alice@example.com"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void installmentEmailNotForErasedAccountButIgnoresOptOut() {
        InstallmentEmailSender sender = mock(InstallmentEmailSender.class);
        UserPreference off = new UserPreference();
        off.setNotificationsEnabled(false);
        when(prefs.findByUserId(7L)).thenReturn(Optional.of(off));
        InstallmentEmailListener l = new InstallmentEmailListener(users, prefs, sender);
        InstallmentNoticeEvent ev = new InstallmentNoticeEvent(7L, 1L, "ORD-1", "APPROVED", null, null, 6, null, null);
        l.onInstallmentNotice(ev);
        verify(sender).send(eq("alice@example.com"), eq("APPROVED"), eq(1L), eq("ORD-1"), any(), any(), eq(6), any(), any());
        alice.setAccountStatus("DELETED");
        l.onInstallmentNotice(ev);
        verify(sender, org.mockito.Mockito.times(1)).send(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    // ---------------- hand-over email listener ----------------
    @Test
    void deliveredEmailSkipsRenderingWhenMailCannotBeSent() {
        OrderDocumentService docs = mock(OrderDocumentService.class);
        DeliveryEmailSender sender = mock(DeliveryEmailSender.class);
        when(sender.canSend()).thenReturn(false);
        List<Runnable> ran = new java.util.ArrayList<>();
        new OrderDeliveredEmailListener(docs, sender, ran::add).onOrderDelivered(new OrderDeliveredEvent(5L));
        assertThat(ran).isEmpty();
        verify(docs, never()).forDeliveredOrder(anyLong());
    }

    @Test
    void deliveredEmailQueueFullIsSwallowedAndRenderFailureNeverEscapes() {
        OrderDocumentService docs = mock(OrderDocumentService.class);
        DeliveryEmailSender sender = mock(DeliveryEmailSender.class);
        when(sender.canSend()).thenReturn(true);
        new OrderDeliveredEmailListener(docs, sender, r -> { throw new RejectedExecutionException("full"); })
            .onOrderDelivered(new OrderDeliveredEvent(5L));
        when(docs.forDeliveredOrder(5L)).thenThrow(new IllegalStateException("pdf"));
        new OrderDeliveredEmailListener(docs, sender, Runnable::run).onOrderDelivered(new OrderDeliveredEvent(5L));
        verify(sender, never()).send(any());
    }

    @Test
    void deliveredEmailSendsWhenDocumentsFound() {
        OrderDocumentService docs = mock(OrderDocumentService.class);
        DeliveryEmailSender sender = mock(DeliveryEmailSender.class);
        when(sender.canSend()).thenReturn(true);
        OrderDocumentService.DeliveryDocuments d = new OrderDocumentService.DeliveryDocuments("a@b.c", "A", "ORD-5", List.of(), false, true);
        when(docs.forDeliveredOrder(5L)).thenReturn(Optional.of(d));
        when(sender.send(d)).thenReturn(true);
        new OrderDeliveredEmailListener(docs, sender, Runnable::run).onOrderDelivered(new OrderDeliveredEvent(5L));
        verify(sender).send(d);
    }

    // ---------------- sender flags ----------------
    @Test
    void mailSendersObeyEnabledFlagHostAndBlankAddress() {
        JavaMailSender mail = mock(JavaMailSender.class);
        MaintenanceEmailSender off = new MaintenanceEmailSender(mail, "f@x", "smtp", false, "");
        off.send("a@b.c", "B", "F1", "P6M");
        new MaintenanceEmailSender(mail, "f@x", "", true, "").send("a@b.c", "B", "F1", "P6M");
        new MaintenanceEmailSender(mail, "f@x", "smtp", true, "").send(" ", "B", "F1", "P6M");
        new MaintenanceEmailSender(mail, "f@x", "smtp", true, "").send(null, "B", "F1", "P6M");
        new StockAlertEmailSender(mail, "f@x", "smtp", false, "").send(1L, "a@b.c", "B", "R", "b");
        new StockAlertEmailSender(mail, "f@x", " ", true, "").send(1L, "a@b.c", "B", "R", "b");
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailSendersSendAndSwallowMailFailures() {
        JavaMailSender mail = mock(JavaMailSender.class);
        new StockAlertEmailSender(mail, "f@x", "smtp", true, "https://shop.example.com/").send(1L, "a@b.c", "Bike", "Red", "my bike");
        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(cap.capture());
        assertThat(cap.getValue().getText()).contains("https://shop.example.com/product/my%20bike");
        assertThat(cap.getValue().getTo()).containsExactly("a@b.c");
        doThrow(new MailSendException("down")).when(mail).send(any(SimpleMailMessage.class));
        new StockAlertEmailSender(mail, "f@x", "smtp", true, "").send(1L, "a@b.c", "Bike", "Red", "bike");
        new MaintenanceEmailSender(mail, "f@x", "smtp", true, "").send("a@b.c", "Bike", "F1", "P6M");
    }
}
