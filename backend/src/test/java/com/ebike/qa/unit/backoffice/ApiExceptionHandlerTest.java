package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.common.exception.ApiExceptionHandler;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void responseStatus_usesReasonAsMessage() {
        var res = handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.CONFLICT, "Trùng"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().message()).isEqualTo("Trùng");
        assertThat(res.getBody().status()).isEqualTo(409);
        assertThat(res.getBody().error()).isEqualTo("Conflict");
        assertThat(res.getBody().timestamp()).isNotBlank();
    }

    @Test
    void responseStatus_withoutReason_fallsBackToReasonPhrase() {
        var res = handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThat(res.getBody().message()).isEqualTo("Not Found");
        var blank = handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.FORBIDDEN, "   "));
        assertThat(blank.getBody().message()).isEqualTo("Forbidden");
    }

    @Test
    void unreadableBody_is400_withGenericMessage_notLeakingParserDetails() {
        var ex = new HttpMessageNotReadableException("Unexpected character at secret.Class[\"field\"]", new MockHttpInputMessage(new byte[0]));
        var res = handler.handleUnreadableBody(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).isEqualTo("Dữ liệu gửi lên không hợp lệ.").doesNotContain("secret");
    }

    @Test
    void typeMismatch_is400_namingTheParameter() {
        var ex = new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null, new NumberFormatException());
        var res = handler.handleBadArgument(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("page");
    }

    @Test
    void missingParameter_is400_namingTheParameter() {
        var res = handler.handleMissingParameter(new MissingServletRequestParameterException("amount", "BigDecimal"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("amount");
    }

    @Test
    void lockFailure_is409_askingToRetry() {
        var res = handler.handleLockFailure(new CannotAcquireLockException("lock"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().message()).contains("thử lại");
    }

    /**
     * Black-box finding: a 300-character first name ends as HTTP 500. The handler only translates ResponseStatusException and a few
     * binding errors; a DataIntegrityViolationException (value too long, duplicate key that slipped past the pre-check) is not handled.
     * Expected: a 400/409 ApiErrorResponse. Actual: no handler, so Spring answers 500. ApiExceptionHandler.java (no such @ExceptionHandler).
     */
    @Test
    void regression_dataIntegrityViolation_hasNoHandler_soTheClientSees500() {
        boolean handles = Arrays.stream(ApiExceptionHandler.class.getDeclaredMethods())
            .map(m -> m.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class))
            .filter(a -> a != null)
            .flatMap(a -> Arrays.stream(a.value()))
            .anyMatch(c -> c.isAssignableFrom(org.springframework.dao.DataIntegrityViolationException.class));
        assertThat(handles).isTrue();
    }
}
