package com.ebike.service.notification;

import com.ebike.dto.notification.response.NotificationResponse;
import com.ebike.dto.notification.response.UnreadNotificationCountResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface NotificationService {

    List<NotificationResponse> getNotifications(Authentication authentication);

    UnreadNotificationCountResponse getUnreadCount(Authentication authentication);

    NotificationResponse markAsRead(Long id, Authentication authentication);

    List<NotificationResponse> markAllAsRead(Authentication authentication);

    Long getCurrentUserId(Authentication authentication);
}
