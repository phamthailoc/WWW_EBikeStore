package com.ebike.dto.order.request;

import java.util.UUID;

public record GuestOrderLinkRequest(UUID emailVerificationSessionId) {
}
