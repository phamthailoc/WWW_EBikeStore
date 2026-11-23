package com.ebike.common.exception;

import com.ebike.common.i18n.RequestLanguage;
import java.time.OffsetDateTime;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null || exception.getReason().isBlank()
            ? status.getReasonPhrase()
            : exception.getReason();

        return ResponseEntity.status(status)
            .body(new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                OffsetDateTime.now().toString()
            ));
    }

    /** A body that is not valid JSON, or a field of the wrong type (a word where a number belongs). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return badRequest(RequestLanguage.text("Dữ liệu gửi lên không hợp lệ.", "The data sent is not valid."));
    }

    /** A query or path value of the wrong type, such as ?page=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleBadArgument(MethodArgumentTypeMismatchException exception) {
        return badRequest(RequestLanguage.text("Giá trị của tham số \"" + exception.getName() + "\" không hợp lệ.",
            "The value of the parameter \"" + exception.getName() + "\" is not valid."));
    }

    /** A required query parameter that is missing, such as a quote without ?amount=. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest(RequestLanguage.text("Thiếu tham số \"" + exception.getParameterName() + "\".",
            "The parameter \"" + exception.getParameterName() + "\" is missing."));
    }

    /** A row lock that could not be had, or a deadlock the database broke: nothing was saved, so the person can simply try again. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleLockFailure(PessimisticLockingFailureException exception) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), status.getReasonPhrase(),
            RequestLanguage.text("Dữ liệu đang được người khác xử lý cùng lúc. Vui lòng tải lại và thử lại.", "Someone else is changing the same data at this moment. Please reload and try again."),
            OffsetDateTime.now().toString()));
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .body(new ApiErrorResponse(status.value(), status.getReasonPhrase(), message, OffsetDateTime.now().toString()));
    }

    public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String timestamp
    ) {
    }
}
