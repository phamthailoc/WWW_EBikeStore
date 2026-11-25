package com.ebike.dto.admin.response;

/** A category with how many bikes it holds; {@code canDelete} is false while it still holds one. */
public record CatalogCategoryRow(Integer id, String name, String slug, String description, boolean active, long productCount, boolean canDelete) {
}
