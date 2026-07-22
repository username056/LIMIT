package com.c203.limit.global.exception;

import com.c203.limit.global.response.ApiErrorResponse;
import java.util.List;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error(
                    "business exception: code={}, message={}",
                    errorCode.getCode(),
                    exception.getMessage(),
                    exception);
        } else {
            log.warn(
                    "business exception: code={}, message={}",
                    errorCode.getCode(),
                    exception.getMessage());
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode, exception.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldError> fieldErrors =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new ApiErrorResponse.FieldError(
                                                error.getField(),
                                                defaultReason(error.getDefaultMessage())))
                        .toList();
        log.warn(
                "request validation failed: fields={}",
                fieldErrors.stream().map(ApiErrorResponse.FieldError::field).toList());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors, traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return errorResponse(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception) {
        return errorResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception) {
        return errorResponse(ErrorCode.INVALID_INPUT_VALUE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return errorResponse(ErrorCode.INVALID_TYPE_VALUE);
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMissingRequiredValue(Exception exception) {
        return errorResponse(ErrorCode.MISSING_REQUEST_PARAMETER);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNotFound(NoResourceFoundException exception) {
        log.debug("resource not found: {}", exception.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("unhandled exception", exception);
        return errorResponse(ErrorCode.INTERNAL_ERROR);
    }

    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleClientAbort(Exception exception) {
        log.debug("client connection aborted or response is no longer usable");
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode, traceId()));
    }

    private String defaultReason(String reason) {
        return reason == null || reason.isBlank() ? "검증에 실패했습니다." : reason;
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}
