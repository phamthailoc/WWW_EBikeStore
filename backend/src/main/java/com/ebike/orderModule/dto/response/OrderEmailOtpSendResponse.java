package com.ebike.orderModule.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderEmailOtpSendResponse(
    UUID verificationSessionId,
    String email,
    LocalDateTime expiresAt,
    Integer resendAfterSeconds,
    Integer maxAttempts
) {
}
