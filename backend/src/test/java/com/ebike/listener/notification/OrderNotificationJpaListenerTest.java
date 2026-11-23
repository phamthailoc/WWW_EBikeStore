package com.ebike.listener.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ebike.event.notification.OrderDeliveredEvent;
import com.ebike.event.notification.OrderStatusChangedEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.service.installment.InstallmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** V24: a rejected instalment application cancels its order but the customer is told once (by the instalment notice), not twice. */
class OrderNotificationJpaListenerTest {

    private ApplicationEventPublisher publisher;
    private final OrderNotificationJpaListener listener = new OrderNotificationJpaListener();

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        new NotificationEventBridge().setApplicationEventPublisher(publisher);
    }

    @AfterEach
    void tearDown() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private static Order cancelledOrder(String reason, boolean suppressNotice) {
        User user = new User();
        user.setId(7L);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-10");
        order.setUser(user);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setSuppressStatusNotice(suppressNotice);
        ReflectionTestUtils.setField(order, "previousStatusForNotification", OrderStatus.PENDING);
        return order;
    }

    @Test
    void aCancellationForARejectedApplicationSendsNoOrderNotice() {
        listener.afterUpdate(cancelledOrder(InstallmentService.REJECTED_REASON_PREFIX + "Thu nhập chưa đủ", true));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aStaffCancellationWhoseReasonHappensToStartTheSameWayStillSendsTheNotice() {
        listener.afterUpdate(cancelledOrder(InstallmentService.REJECTED_REASON_PREFIX + "gõ tay", false));
        verify(publisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    private static Order deliveredOrder(User user, OrderStatus before) {
        Order order = new Order();
        order.setId(11L);
        order.setOrderNumber("ORD-11");
        order.setUser(user);
        order.setStatus(OrderStatus.DELIVERED);
        ReflectionTestUtils.setField(order, "previousStatusForNotification", before);
        return order;
    }

    @Test
    void handingOverAnOrderSendsTheNoticeAndAsksForTheDocumentsEmail() {
        User user = new User();
        user.setId(7L);

        listener.afterUpdate(deliveredOrder(user, OrderStatus.SHIPPED));

        verify(publisher).publishEvent(any(OrderStatusChangedEvent.class));
        verify(publisher).publishEvent(new OrderDeliveredEvent(11L));
    }

    @Test
    void aGuestOrderHasNoNoticeButStillGetsTheDocumentsEmail() {
        listener.afterUpdate(deliveredOrder(null, OrderStatus.READY_FOR_PICKUP));

        verify(publisher).publishEvent(new OrderDeliveredEvent(11L));
        verify(publisher, never()).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void savingAnOrderThatWasAlreadyDeliveredSendsNothing() {
        User user = new User();
        user.setId(7L);

        listener.afterUpdate(deliveredOrder(user, OrderStatus.DELIVERED));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void anotherCancellationStillSendsTheOrderNotice() {
        listener.afterUpdate(cancelledOrder("Đổi ý", false));
        verify(publisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void flushingTheSameDeliveredOrderTwiceAnnouncesItOnce() {
        User user = new User();
        user.setId(7L);
        Order order = deliveredOrder(user, OrderStatus.SHIPPED);

        listener.afterUpdate(order);
        listener.afterUpdate(order);

        verify(publisher, times(1)).publishEvent(new OrderDeliveredEvent(11L));
        verify(publisher, times(1)).publishEvent(any(OrderStatusChangedEvent.class));
    }
}
