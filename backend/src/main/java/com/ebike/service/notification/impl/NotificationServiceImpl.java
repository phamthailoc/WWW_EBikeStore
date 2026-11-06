package com.ebike.service.notification.impl;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.dto.notification.response.NotificationResponse;
import com.ebike.dto.notification.response.UnreadNotificationCountResponse;
import com.ebike.model.entity.notification.Notification;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.service.notification.NotificationService;
import com.ebike.service.notification.NotificationStreamService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationStreamService notificationStreamService;

    public NotificationServiceImpl(
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        NotificationStreamService notificationStreamService
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationStreamService = notificationStreamService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return new UnreadNotificationCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(user.getId()));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long id, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }
        NotificationResponse response = toResponse(notificationRepository.save(notification));
        notificationStreamService.sendUnreadCount(user.getId());
        return response;
    }

    @Override
    @Transactional
    public List<NotificationResponse> markAllAsRead(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = notificationRepository.findByUserIdAndReadAtIsNull(user.getId());
        notifications.forEach(notification -> notification.setReadAt(now));
        List<NotificationResponse> responses = notificationRepository.saveAll(notifications).stream()
            .map(this::toResponse)
            .toList();
        notificationStreamService.sendUnreadCount(user.getId());
        return responses;
    }

    @Override
    public Long getCurrentUserId(Authentication authentication) {
        return getAuthenticatedUser(authentication).getId();
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType().name(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getTargetUrl(),
            notification.getOrderId(),
            notification.getPaymentId(),
            notification.getReadAt() != null,
            notification.getReadAt(),
            notification.getCreatedAt()
        );
    }
}
