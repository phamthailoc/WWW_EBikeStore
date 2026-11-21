package com.ebike.listener.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private static Order cancelledOrder(String reason) {
        User user = new User();
        user.setId(7L);
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-10");
        order.setUser(user);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        ReflectionTestUtils.setField(order, "previousStatusForNotification", OrderStatus.PENDING);
        return order;
    }

    @Test
    void aCancellationForARejectedApplicationSendsNoOrderNotice() {
        listener.afterUpdate(cancelledOrder(InstallmentService.REJECTED_REASON_PREFIX + "Thu nhập chưa đủ"));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void anotherCancellationStillSendsTheOrderNotice() {
        listener.afterUpdate(cancelledOrder("Đổi ý"));
        verify(publisher).publishEvent(any(OrderStatusChangedEvent.class));
    }
}
