package com.ebike.dto.manager.request;

import java.time.LocalDateTime;

public record ManagerPickupDeadlineRequest(
    LocalDateTime pickupDeadlineAt
) {
}
