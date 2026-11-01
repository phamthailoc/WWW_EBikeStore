package com.ebike.dto.auth.request;

public record LoginRequest(
    String usernameOrEmail,
    String password
) {
}
