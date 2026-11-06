package com.ebike.dto.auth.request;

public record UpdateProfileRequest(
    String firstName,
    String lastName,
    String email
) {
}
