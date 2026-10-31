package com.ebike.authModule.dto.request;

public record UpdateProfileRequest(
    String firstName,
    String lastName,
    String email
) {
}
