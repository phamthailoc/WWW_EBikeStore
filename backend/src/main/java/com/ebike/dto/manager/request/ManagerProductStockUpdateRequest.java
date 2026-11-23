package com.ebike.dto.manager.request;

/** The new stock figure and, optionally (V26), why it changes: the note is written on the stock ledger line. */
public record ManagerProductStockUpdateRequest(
    Integer stockQuantity,
    String note
) {
}
