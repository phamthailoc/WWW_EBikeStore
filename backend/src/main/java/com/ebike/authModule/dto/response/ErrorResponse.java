package com.ebike.authModule.dto.response;

import java.time.LocalDateTime;

public record ErrorResponse(
    Integer status,
    String message,
    String path,
    LocalDateTime timestamp
) {
}
