package com.ebike.dto.manager.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ManagerCustomerResponse(
    Long id,
    String username,
    String email,
    String firstName,
    String lastName,
    Boolean active,
    Boolean verified,
    long orderCount,
    BigDecimal totalSpent,
    LocalDateTime createdAt
) {
}
