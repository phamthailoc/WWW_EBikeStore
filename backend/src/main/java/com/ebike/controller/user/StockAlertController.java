package com.ebike.controller.user;

import com.ebike.common.exception.ApiExceptionHandler.ApiErrorResponse;
import com.ebike.dto.product.request.StockAlertRequest;
import com.ebike.dto.product.response.StockAlertResponse;
import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockAlertSubscription;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /** 201 when a request was made, 200 when the customer was already waiting for this colour. */
    @PostMapping
    public ResponseEntity<StockAlertResponse> subscribe(Authentication authentication, @RequestBody StockAlertRequest request) {
        StockAlertSubscription result = stockAlertService.subscribe(authentication.getName(), request.variantId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.alert());
    }

    @DeleteMapping("/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(Authentication authentication, @PathVariable Long variantId) {
        stockAlertService.unsubscribe(authentication.getName(), variantId);
    }

    /** A body that is not JSON or has a non-numeric variantId: answer in the page's language, not "Bad Request". */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadableBody() {
        return error(HttpStatus.BAD_REQUEST, "Vui lòng chọn màu xe.");
    }

    /** DELETE /api/v1/stock-alerts/abc: the colour in the path is not a number. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> badColourInPath() {
        return error(HttpStatus.BAD_REQUEST, "Vui lòng chọn màu xe.");
    }

    /** A body that is not sent as JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> wrongContentType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Yêu cầu không hợp lệ. Vui lòng thử lại.");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            status.value(), status.getReasonPhrase(), message, OffsetDateTime.now().toString()));
    }
}
