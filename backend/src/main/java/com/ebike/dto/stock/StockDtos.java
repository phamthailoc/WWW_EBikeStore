package com.ebike.dto.stock;

import java.time.LocalDateTime;
import java.util.List;

/** V26: the requests and answers of the stock receipt and stock history pages. */
public final class StockDtos {

    private StockDtos() {
    }

    public record ReceiptLineRequest(Long productId, Long variantId, Integer quantity) {
    }

    public record ReceiptCreateRequest(String supplierName, String note, List<ReceiptLineRequest> lines) {
    }

    public record ReceiptLineResponse(Long productId, Long variantId, String productName, String colorName, int quantity) {
    }

    public record ReceiptResponse(
        Long id,
        String number,
        String supplierName,
        String note,
        String createdByName,
        LocalDateTime createdAt,
        int lineCount,
        int totalQuantity,
        List<ReceiptLineResponse> lines
    ) {
    }

    public record ReceiptPage(List<ReceiptResponse> items, boolean hasMore) {
    }

    /** One colour a receipt can be written for. */
    public record OptionColour(Long id, String colorName, String colorHex, boolean active, int stock) {
    }

    /** One bike in the receipt form's pick list; a bike with no active colour is received as a whole (colours is empty or only withdrawn ones). */
    public record OptionProduct(Long id, String name, boolean active, boolean hasActiveColours, int stock, List<OptionColour> colours) {
    }

    public record MovementResponse(
        Long id,
        String reason,
        int change,
        int balanceAfter,
        Long variantId,
        String colorName,
        Long orderId,
        String orderNumber,
        Long receiptId,
        String receiptNumber,
        String actorName,
        String note,
        LocalDateTime createdAt
    ) {
    }

    /** Where one colour stands: its stock, what the ledger lines add up to, and whether the two agree. */
    public record BalanceResponse(Long variantId, String colorName, boolean active, int stock, long ledgerTotal, boolean matches) {
    }

    public record HistoryResponse(
        Long productId,
        String productName,
        int stockQuantity,
        List<BalanceResponse> balances,
        List<MovementResponse> items,
        boolean hasMore
    ) {
    }
}
