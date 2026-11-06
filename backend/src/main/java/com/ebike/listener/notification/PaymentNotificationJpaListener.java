package com.ebike.listener.notification;

import com.ebike.event.notification.PaymentStatusChangedEvent;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentStatus;
import jakarta.persistence.PostUpdate;

public class PaymentNotificationJpaListener {

    @PostUpdate
    public void afterUpdate(Payment payment) {
        if (payment == null || payment.getOrder() == null || payment.getPreviousPaymentStatusForNotification() == null) {
            return;
        }
        if (payment.getPaymentStatus() == payment.getPreviousPaymentStatusForNotification()) {
            return;
        }
        if (payment.getPaymentStatus() != PaymentStatus.PAID && payment.getPaymentStatus() != PaymentStatus.FAILED) {
            return;
        }
        Order order = payment.getOrder();
        if (order.getUser() == null) {
            return;
        }
        NotificationEventBridge.publish(new PaymentStatusChangedEvent(
            order.getUser().getId(),
            order.getId(),
            payment.getId(),
            order.getOrderNumber(),
            payment.getPaymentStatus().name()
        ));
    }
}
