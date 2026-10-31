package com.ebike.adminModule.dto.response;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
    Long id,
    String actor,
    String action,
    String target,
    String ipAddress,
    LocalDateTime createdAt
) {
}
