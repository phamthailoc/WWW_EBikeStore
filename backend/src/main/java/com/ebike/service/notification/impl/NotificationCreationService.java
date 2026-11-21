package com.ebike.service.notification.impl;

import com.ebike.config.AsyncConfiguration;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.model.entity.notification.Notification;
import com.ebike.model.entity.notification.NotificationType;
import com.ebike.event.notification.OrderCreatedEvent;
import com.ebike.event.notification.OrderStatusChangedEvent;
import com.ebike.event.notification.PickupDeadlineChangedEvent;
import com.ebike.event.notification.PaymentStatusChangedEvent;
import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.event.notification.WarrantyClaimStatusChangedEvent;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.notification.NotificationStreamService;
import com.ebike.service.warranty.WarrantyMessages;
import com.ebike.service.testride.TestRideMessages;
import com.ebike.service.stock.StockAlertMessages;
import org.springframework.scheduling.annotation.Async;
import java.util.Optional;
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
    private final UserPreferenceRepository userPreferenceRepository;

    public NotificationCreationService(
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        NotificationStreamService notificationStreamService,
        UserPreferenceRepository userPreferenceRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationStreamService = notificationStreamService;
        this.userPreferenceRepository = userPreferenceRepository;
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

    // The store moved the day it keeps the bike; the customer asked for it, and this lets them confirm it is right.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePickupDeadlineChanged(PickupDeadlineChangedEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> createNotification(
            user,
            NotificationType.ORDER_STATUS,
            "Đổi lịch lấy xe",
            "Cửa hàng sẽ giữ xe của đơn hàng " + event.orderNumber() + " đến "
                + event.pickupDeadlineAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
                + ". Nếu chưa đúng với lịch bạn đã hẹn, vui lòng liên hệ cửa hàng.",
            event.orderId(),
            event.paymentId()
        ));
    }

    // V22: a colour the customer asked to be told about is back; the link opens the bike page, not the orders page.
    // Runs on its own pool so a restock with hundreds of waiting customers does not hold up the manager's request.
    @Async(AsyncConfiguration.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleStockAlertTriggered(StockAlertTriggeredEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> createNotification(
            user,
            NotificationType.STOCK_ALERT,
            StockAlertMessages.notificationTitle(event.language()),
            StockAlertMessages.notificationBody(event.language(), event.productName(), event.colorName()),
            null,
            null,
            StockAlertMessages.notificationPath(event.productSlug()),
            false
        ));
    }

    // V20: the staff moved a warranty request; the link opens the customer's warranty book.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleWarrantyClaimStatusChanged(WarrantyClaimStatusChangedEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            // the profile is read once: it gives the language of the words and the opt-out of the notification
            Optional<UserPreference> preference = userPreferenceRepository.findByUserId(user.getId());
            String language = languageOf(preference);
            createNotification(user, preference, NotificationType.WARRANTY_CLAIM,
                WarrantyMessages.claimTitle(language),
                WarrantyMessages.claimBody(language, event.productName(), event.frameNumber(), event.status()),
                null, null, "/customer/warranty", true);
        });
    }

    // V20: a vehicle reached a maintenance milestone (counted from the delivery).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleMaintenanceDue(MaintenanceDueEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            Optional<UserPreference> preference = userPreferenceRepository.findByUserId(user.getId());
            String language = languageOf(preference);
            createNotification(user, preference, NotificationType.MAINTENANCE_DUE,
                WarrantyMessages.maintenanceTitle(language),
                WarrantyMessages.maintenanceBody(language, event.productName(), event.frameNumber(), event.milestone()),
                null, null, "/customer/warranty", true);
        });
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
        createNotification(user, type, title, message, orderId, paymentId, "/customer/orders", true);
    }

    private void createNotification(
        User user,
        NotificationType type,
        String title,
        String message,
        Long orderId,
        Long paymentId,
        String targetUrl
    ) {
        createNotification(user, type, title, message, orderId, paymentId, targetUrl, true);
    }

    private void createNotification(
        User user,
        NotificationType type,
        String title,
        String message,
        Long orderId,
        Long paymentId,
        String targetUrl,
        boolean honourOptOut
    ) {
        // the profile is only looked up when it decides something
        createNotification(user, honourOptOut ? userPreferenceRepository.findByUserId(user.getId()) : Optional.empty(),
            type, title, message, orderId, paymentId, targetUrl, honourOptOut);
    }

    /** {@code preference} is the profile the caller has already read, so one notification costs one lookup. */
    private void createNotification(
        User user,
        Optional<UserPreference> preference,
        NotificationType type,
        String title,
        String message,
        Long orderId,
        Long paymentId,
        String targetUrl,
        boolean honourOptOut
    ) {
        // An erased account (V6) gets nothing new: the shop may still close its old orders, but no data is added to it.
        if ("DELETED".equals(user.getAccountStatus())) {
            return;
        }
        // A customer who switched notifications off in the profile gets none; no saved setting means on.
        // Not for a back-in-stock alert: the customer asked for that one on purpose (honourOptOut is false), and
        // dropping it silently would use up the request.
        boolean optedOut = honourOptOut && preference
            .map(saved -> Boolean.FALSE.equals(saved.getNotificationsEnabled()))
            .orElse(false);
        if (optedOut) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetUrl(targetUrl);
        notification.setOrderId(orderId);
        notification.setPaymentId(paymentId);
        notificationRepository.save(notification);
        notificationStreamService.sendUnreadCount(user.getId());
    }

    // V23: a test-ride booking was confirmed, cancelled by the store, missed or closed unconfirmed, or is coming up; the link opens the customer's bookings.
    // The customer asked for this booking, so the notices about it ignore the profile's notification switch (like the back-in-stock alert): a customer whose
    // ride the store cancelled must be told, or they come for nothing.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTestRideNotice(TestRideNoticeEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            Optional<UserPreference> preference = userPreferenceRepository.findByUserId(user.getId());
            String language = languageOf(preference);
            createNotification(user, preference, NotificationType.TEST_RIDE,
                TestRideMessages.title(language, event.kind()),
                TestRideMessages.body(language, event.kind(), event.productName(), event.showroomName(), event.slotStart(), event.note()),
                null, null, "/customer/test-rides", false);
        });
    }

    /** The language saved in the customer's profile ("vi" when there is none), for the words of a notice they did not ask for in a page. */
    private String languageOf(Optional<UserPreference> preference) {
        return preference.map(UserPreference::getLanguage).orElse("vi");
    }

    private String formatOrderStatus(String status) {
        if ("PENDING".equals(status)) {
            return "chờ xác nhận";
        }
        if ("CONFIRMED".equals(status)) {
            return "đã xác nhận";
        }
        if ("PROCESSING".equals(status)) {
            return "đang xử lý";
        }
        if ("READY_FOR_PICKUP".equals(status)) {
            return "chờ lấy xe tại cửa hàng";
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
