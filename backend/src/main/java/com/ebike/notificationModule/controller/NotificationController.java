package com.ebike.notificationModule.controller;

import com.ebike.notificationModule.dto.response.NotificationResponse;
import com.ebike.notificationModule.dto.response.UnreadNotificationCountResponse;
import com.ebike.notificationModule.service.NotificationService;
import com.ebike.notificationModule.service.NotificationStreamService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    public NotificationController(NotificationService notificationService, NotificationStreamService notificationStreamService) {
        this.notificationService = notificationService;
        this.notificationStreamService = notificationStreamService;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications(Authentication authentication) {
        return notificationService.getNotifications(authentication);
    }

    @GetMapping("/stream")
    public SseEmitter stream(Authentication authentication) {
        return notificationStreamService.subscribe(notificationService.getCurrentUserId(authentication));
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse getUnreadCount(Authentication authentication) {
        return notificationService.getUnreadCount(authentication);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id, Authentication authentication) {
        return notificationService.markAsRead(id, authentication);
    }

    @PatchMapping("/read-all")
    public List<NotificationResponse> markAllAsRead(Authentication authentication) {
        return notificationService.markAllAsRead(authentication);
    }
}
