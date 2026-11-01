package com.ebike.service.notification.impl;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.model.entity.notification.Notification;
import com.ebike.model.entity.notification.NotificationType;
import com.ebike.event.notification.OrderCreatedEvent;
import com.ebike.event.notification.OrderStatusChangedEvent;
import com.ebike.event.notification.PaymentStatusChangedEvent;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.service.notification.NotificationStreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class NotificationCreationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationStreamService notificationStreamService;

    public NotificationCreationService(
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        NotificationStreamService notificationStreamService
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationStreamService = notificationStreamService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCreated(OrderCreatedEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> createNotification(
            user,
            NotificationType.ORDER_STATUS,
            "Đã nhận đơn hàng",
            "Đơn hàng " + event.orderNumber() + " đã được tạo và đang chờ xác nhận.",
            event.orderId(),
            event.paymentId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> createNotification(
            user,
            NotificationType.ORDER_STATUS,
            "Cập nhật đơn hàng",
            "Đơn hàng " + event.orderNumber() + " đã chuyển sang trạng thái " + formatOrderStatus(event.status()) + ".",
            event.orderId(),
            event.paymentId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentStatusChanged(PaymentStatusChangedEvent event) {
        boolean paid = "PAID".equals(event.paymentStatus());
        userRepository.findById(event.userId()).ifPresent(user -> createNotification(
            user,
            paid ? NotificationType.PAYMENT_SUCCESS : NotificationType.PAYMENT_FAILED,
            paid ? "Thanh toán thành công" : "Thanh toán thất bại",
            "Thanh toán cho đơn hàng " + event.orderNumber() + (paid ? " đã thành công." : " chưa thành công. Vui lòng thử lại hoặc chọn hình thức khác."),
            event.orderId(),
            event.paymentId()
        ));
    }

    private void createNotification(
        User user,
        NotificationType type,
        String title,
        String message,
        Long orderId,
        Long paymentId
    ) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetUrl("/customer/orders");
        notification.setOrderId(orderId);
        notification.setPaymentId(paymentId);
        notificationRepository.save(notification);
        notificationStreamService.sendUnreadCount(user.getId());
    }

    private String formatOrderStatus(String status) {
        if ("PENDING".equals(status)) {
            return "chờ xác nhận";
        }
        if ("CONFIRMED".equals(status)) {
            return "đã xác nhận";
        }
        if ("CANCELLATION_REQUESTED".equals(status)) {
            return "đang chờ duyệt hủy";
        }
        if ("PROCESSING".equals(status)) {
            return "đang xử lý";
        }
        if ("SHIPPED".equals(status)) {
            return "đang giao";
        }
        if ("DELIVERED".equals(status)) {
            return "đã giao";
        }
        if ("CANCELLED".equals(status)) {
            return "đã hủy";
        }
        return status;
    }
}
