package com.ebike.authModule.dto.response;

import java.time.LocalDateTime;
import java.util.Set;

public record EnhancedAuthResponse(
    Long userId,
    String username,
    String email,
    String fullName,
    String token,
    Set<String> roles,
    Boolean active,
    Boolean verified,
    LocalDateTime issuedAt,
    LocalDateTime expiresAt
) {
}
