package com.ebike.dto.support;

public record SupportRequestCreateRequest(
    String customerName,
    String email,
    String phone,
    String subject,
    String message
) {
}
