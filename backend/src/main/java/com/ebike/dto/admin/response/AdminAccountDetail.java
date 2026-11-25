package com.ebike.dto.admin.response;

import java.time.LocalDateTime;
import java.util.List;

/** One account in full for the admin's detail view. There is no password field, not even the hash. */
public record AdminAccountDetail(
    Long id,
    String firstName,
    String lastName,
    String username,
    String email,
    String accountType,
    String status,
    Boolean verified,
    List<String> roles,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long orderCount,
    boolean canDelete
) {
}
