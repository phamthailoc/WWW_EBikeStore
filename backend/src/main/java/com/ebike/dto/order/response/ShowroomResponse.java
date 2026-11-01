package com.ebike.dto.order.response;

public record ShowroomResponse(
    Long id,
    String name,
    String city,
    String district,
    String address,
    String phone,
    String openingHours,
    Boolean active
) {
}
