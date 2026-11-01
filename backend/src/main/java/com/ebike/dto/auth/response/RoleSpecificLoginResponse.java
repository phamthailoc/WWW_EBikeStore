package com.ebike.dto.auth.response;

public record RoleSpecificLoginResponse(
    String role,
    Object roleData,
    EnhancedAuthResponse authResponse
) {
}
