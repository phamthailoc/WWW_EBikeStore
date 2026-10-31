package com.ebike.adminModule.dto.response;

import java.util.List;

public record RolePermissionResponse(
    String role,
    String title,
    String description,
    List<String> permissions
) {
}
