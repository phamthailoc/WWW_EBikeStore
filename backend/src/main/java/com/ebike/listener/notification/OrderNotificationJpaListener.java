package com.ebike.listener.notification;

import com.ebike.event.notification.OrderCreatedEvent;
import com.ebike.event.notification.OrderDeliveredEvent;
import com.ebike.event.notification.OrderStatusChangedEvent;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;

public class OrderNotificationJpaListener {

    @PostPersist
    public void afterCreate(Order order) {
        if (order == null || order.getUser() == null) {
            return;
        }
        Payment payment = order.getPayment();
        NotificationEventBridge.publish(new OrderCreatedEvent(
            order.getUser().getId(),
            order.getId(),
            payment == null ? null : payment.getId(),
            order.getOrderNumber(),
            order.getStatus().name()
        ));
    }

    @PostUpdate
    public void afterUpdate(Order order) {
        if (order == null || order.getPreviousStatusForNotification() == null) {
            return;
        }
        if (order.getStatus() == order.getPreviousStatusForNotification()) {
            return;
        }
        // V27: the receipt and the warranty card are emailed at hand-over, also for a guest order (it has no account to notify)
        if (order.getStatus() == OrderStatus.DELIVERED) {
            NotificationEventBridge.publish(new OrderDeliveredEvent(order.getId()));
        }
        if (order.getUser() == null) {
            return;
        }
        // a rejected instalment application cancels its order, and the customer already gets the notice and the email of the rejection (with the reason):
        // the code that did it says so with a flag, so the words of a cancellation reason decide nothing
        if (order.isSuppressStatusNotice()) {
            return;
        }
        Payment payment = order.getPayment();
        NotificationEventBridge.publish(new OrderStatusChangedEvent(
            order.getUser().getId(),
            order.getId(),
            payment == null ? null : payment.getId(),
            order.getOrderNumber(),
            order.getStatus().name()
        ));
    }
}
