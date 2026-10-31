package com.ebike.authModule.dto.request;

public record RegisterRequest(
    String username,
    String email,
    String password,
    String firstName,
    String lastName
) {
}
