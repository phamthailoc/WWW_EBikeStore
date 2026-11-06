package com.ebike.dto.auth.response;

import java.util.Set;

public record AuthResponse(
    Long userId,
    String username,
    String email,
    String fullName,
    String token,
    Set<String> roles
) {
}
