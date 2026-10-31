package com.ebike.notificationModule.listener;

import com.ebike.notificationModule.event.PaymentStatusChangedEvent;
import com.ebike.orderModule.entity.Order;
import com.ebike.orderModule.entity.Payment;
import com.ebike.orderModule.entity.PaymentStatus;
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
