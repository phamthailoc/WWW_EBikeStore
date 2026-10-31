package com.ebike.adminModule.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AdminAccountResponse(
    Long id,
    String name,
    String username,
    String email,
    String accountType,
    String role,
    String status,
    Boolean verified,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<String> roles
) {
}
