package com.ebike.shared.exception;

import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String timestamp
    ) {
    }
}
