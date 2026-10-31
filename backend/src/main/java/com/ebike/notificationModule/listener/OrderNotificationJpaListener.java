package com.ebike.notificationModule.listener;

import com.ebike.notificationModule.event.OrderCreatedEvent;
import com.ebike.notificationModule.event.OrderStatusChangedEvent;
import com.ebike.orderModule.entity.Order;
import com.ebike.orderModule.entity.Payment;
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
        if (order == null || order.getUser() == null || order.getPreviousStatusForNotification() == null) {
            return;
        }
        if (order.getStatus() == order.getPreviousStatusForNotification()) {
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
