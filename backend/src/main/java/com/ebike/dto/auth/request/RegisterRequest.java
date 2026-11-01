package com.ebike.dto.auth.request;

public record RegisterRequest(
    String username,
    String email,
    String password,
    String firstName,
    String lastName
) {
}
