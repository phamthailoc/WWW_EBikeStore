package com.ebike.service.notification;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationStreamService {

    SseEmitter subscribe(Long userId);

    void sendUnreadCount(Long userId);
}
