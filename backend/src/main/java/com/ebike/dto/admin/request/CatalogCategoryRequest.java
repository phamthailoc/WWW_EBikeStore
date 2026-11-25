package com.ebike.dto.admin.request;

public record CatalogCategoryRequest(String name, String slug, String description, Boolean active) {
}
