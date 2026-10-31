package com.ebike.authModule.dto.request;

public record LoginRequest(
    String usernameOrEmail,
    String password
) {
}
