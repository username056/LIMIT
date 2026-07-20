package com.c203.limit.global.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.c203.limit.global.response.ApiErrorResponse;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode, exception.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(
                        error.getField(), error.getRejectedValue(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors, traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST_BODY.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST_BODY, traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) throws Exception {
        if (exception instanceof org.springframework.web.ErrorResponse) {
            // 404, 405 등 상태 코드를 가진 프레임워크 예외는 기본 처리에 위임한다.
            throw exception;
        }
        log.error("unhandled exception", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, traceId()));
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}
