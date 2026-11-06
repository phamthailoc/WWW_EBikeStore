package com.ebike.dto.order.request;

import java.util.UUID;

public record OrderEmailOtpVerifyRequest(
    UUID verificationSessionId,
    String code
) {
}
