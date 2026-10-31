package com.ebike.orderModule.dto.request;

import java.util.UUID;

public record OrderEmailOtpVerifyRequest(
    UUID verificationSessionId,
    String code
) {
}
