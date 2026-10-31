package com.ebike.authModule.dto.request;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword,
    String confirmPassword
) {
}