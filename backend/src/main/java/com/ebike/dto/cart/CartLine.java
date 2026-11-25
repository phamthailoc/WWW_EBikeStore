package com.ebike.dto.cart;

/** One line of the visitor's cart as it is kept in the HTTP session: which bike, which colour, how many. Prices are never kept here. */
public record CartLine(Long id, Long variantId, Integer quantity, String slug) implements java.io.Serializable {
}
