package com.ebike.dto.admin.request;

public record AdminAccountCreateRequest(
    String name,
    String username,
    String email,
    String password,
    String role,
    String status,
    Boolean verified
) {
}
