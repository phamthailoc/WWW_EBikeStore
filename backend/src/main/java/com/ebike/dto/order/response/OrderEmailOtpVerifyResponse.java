package com.ebike.dto.order.response;

import java.util.UUID;

public record OrderEmailOtpVerifyResponse(
    UUID verificationSessionId,
    String email,
    Boolean verified
) {
}
