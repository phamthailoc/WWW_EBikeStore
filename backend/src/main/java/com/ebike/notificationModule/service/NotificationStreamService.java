package com.ebike.notificationModule.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationStreamService {

    SseEmitter subscribe(Long userId);

    void sendUnreadCount(Long userId);
}
