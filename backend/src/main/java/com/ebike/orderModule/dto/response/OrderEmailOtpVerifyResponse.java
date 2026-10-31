package com.ebike.orderModule.dto.response;

import java.util.UUID;

public record OrderEmailOtpVerifyResponse(
    UUID verificationSessionId,
    String email,
    Boolean verified
) {
}
