package com.ebike.controller.user;

import com.ebike.common.exception.ApiExceptionHandler.ApiErrorResponse;
import com.ebike.dto.product.request.StockAlertRequest;
import com.ebike.dto.product.response.StockAlertResponse;
import com.ebike.service.stock.StockAlertService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V22: back-in-stock requests of the signed-in customer. */
@RestController
@RequestMapping("/api/v1/stock-alerts")
public class StockAlertController {

    private final StockAlertService stockAlertService;

    public StockAlertController(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @GetMapping
    public List<StockAlertResponse> list(Authentication authentication) {
        return stockAlertService.list(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockAlertResponse subscribe(Authentication authentication, @RequestBody StockAlertRequest request) {
        return stockAlertService.subscribe(authentication.getName(), request.variantId());
    }

    @DeleteMapping("/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(Authentication authentication, @PathVariable Long variantId) {
        stockAlertService.unsubscribe(authentication.getName(), variantId);
    }

    /** A body that is not JSON or has a non-numeric variantId: answer in the page's language, not "Bad Request". */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadableBody() {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            status.value(), status.getReasonPhrase(), "Vui lòng chọn màu xe.", OffsetDateTime.now().toString()));
    }
}
