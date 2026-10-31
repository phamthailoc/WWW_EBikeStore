package com.ebike.managerModule.dto.response;

import java.math.BigDecimal;

public record ManagerTopProductResponse(
    Long productId,
    String productName,
    long quantitySold,
    BigDecimal revenue
) {
}
