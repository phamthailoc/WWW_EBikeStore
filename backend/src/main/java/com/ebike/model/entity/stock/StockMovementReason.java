package com.ebike.model.entity.stock;

/** V26: why a stock figure changed. OPENING is the stock that was already on the shelves when the ledger was started. */
public enum StockMovementReason {
    RECEIPT,
    SALE,
    CANCEL_RESTORE,
    ADJUSTMENT,
    OPENING
}
