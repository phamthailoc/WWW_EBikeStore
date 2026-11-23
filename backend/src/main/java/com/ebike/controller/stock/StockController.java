package com.ebike.controller.stock;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.dto.stock.StockDtos.HistoryResponse;
import com.ebike.dto.stock.StockDtos.OptionProduct;
import com.ebike.dto.stock.StockDtos.ReceiptCreateRequest;
import com.ebike.dto.stock.StockDtos.ReceiptPage;
import com.ebike.dto.stock.StockDtos.ReceiptResponse;
import com.ebike.service.stock.StockHistoryService;
import com.ebike.service.stock.StockReceiptService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V26: stock receipts (staff with the stock permission) and the stock history of a bike (any staff who can see the orders).
 * Stock changes with every order, so no cache (browser or proxy) may keep an answer.
 */
@RestController
public class StockController {

    private final StockReceiptService receipts;
    private final StockHistoryService history;

    public StockController(StockReceiptService receipts, StockHistoryService history) {
        this.receipts = receipts;
        this.history = history;
    }

    @GetMapping("/api/v1/manager/stock-receipts")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK + "')")
    public ResponseEntity<ReceiptPage> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long beforeId,
        @RequestParam(required = false) Integer limit
    ) {
        return fresh(receipts.list(q, beforeId, limit));
    }

    /** The bikes and colours the receipt form offers. Declared before the {id} route; the two cannot clash because an id is a number. */
    @GetMapping("/api/v1/manager/stock-receipts/options")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK + "')")
    public ResponseEntity<List<OptionProduct>> options() {
        return fresh(receipts.options());
    }

    @GetMapping("/api/v1/manager/stock-receipts/{id:\\d+}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK + "')")
    public ResponseEntity<ReceiptResponse> get(@PathVariable Long id) {
        return fresh(receipts.get(id));
    }

    @PostMapping("/api/v1/manager/stock-receipts")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptResponse create(@RequestBody ReceiptCreateRequest request) {
        return receipts.create(request);
    }

    @GetMapping("/api/v1/manager/products/{productId}/stock-history")
    public ResponseEntity<HistoryResponse> history(
        @PathVariable Long productId,
        @RequestParam(required = false) Long variantId,
        @RequestParam(required = false) String reason,
        @RequestParam(required = false) Long beforeId,
        @RequestParam(required = false) Integer limit
    ) {
        return fresh(history.history(productId, variantId, reason, beforeId, limit));
    }

    private static <T> ResponseEntity<T> fresh(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
