package com.ebike.dto.auth.request;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword,
    String confirmPassword
) {
}