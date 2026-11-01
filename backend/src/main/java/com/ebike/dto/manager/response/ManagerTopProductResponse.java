package com.ebike.dto.manager.response;

import java.math.BigDecimal;

public record ManagerTopProductResponse(
    Long productId,
    String productName,
    long quantitySold,
    BigDecimal revenue
) {
}
