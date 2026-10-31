package com.ebike.notificationModule.dto.response;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String type,
    String title,
    String message,
    String targetUrl,
    Long orderId,
    Long paymentId,
    boolean read,
    LocalDateTime readAt,
    LocalDateTime createdAt
) {
}
