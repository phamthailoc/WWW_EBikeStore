package com.ebike.dto.support;

import java.time.LocalDateTime;

public record SupportRequestResponse(
    Long id,
    String customerName,
    String email,
    String phone,
    String subject,
    String message,
    String status,
    String staffNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
