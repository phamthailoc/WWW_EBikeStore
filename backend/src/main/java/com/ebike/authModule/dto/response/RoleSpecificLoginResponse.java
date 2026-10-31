package com.ebike.authModule.dto.response;

public record RoleSpecificLoginResponse(
    String role,
    Object roleData,
    EnhancedAuthResponse authResponse
) {
}
