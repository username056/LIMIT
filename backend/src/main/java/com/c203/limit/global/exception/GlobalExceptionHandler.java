package com.c203.limit.global.exception;

import com.c203.limit.global.response.ApiErrorResponse;
import java.util.List;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * 사전 검증(existsBy 체크 등)을 통과한 뒤에도 동시성 경합으로 FK 제약을 건드린 경우의 백스톱이다. 서비스 코드에서 직접 잡아 트랜잭션 중간에 복구를 시도하지
     * 않는다 — flush 시점 예외로 트랜잭션이 이미 rollback-only가 된 뒤 계속 작업하면 UnexpectedRollbackException으로 이어질 수
     * 있어서, 롤백이 끝난 뒤 이 핸들러에서만 응답을 매핑한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {
        // DB 드라이버 메시지에는 테이블·제약명 등 내부 정보가 섞여 있어 로그에도 그대로 남기지 않는다.
        log.warn("data integrity violation");
        return errorResponse(ErrorCode.DATA_CONFLICT);
    }

    /**
     * 낙관적 락 버전 충돌({@code ObjectOptimisticLockingFailureException})과 MySQL 데드락(1213,
     * {@code CannotAcquireLockException})은 둘 다 이 공통 상위 타입으로 올라온다. 같은 레코드에
     * 대한 재시도·취소처럼 서비스 코드가 자체 재시도 루프 없이 단일 트랜잭션으로 끝나는 경로에서
     * 발생하며, 별도 처리 없이 던지면 500으로 응답되던 것을 다른 동시성 충돌과 같은 409로 정리한다.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrencyFailure(
            ConcurrencyFailureException exception) {
        log.warn("concurrency failure: {}", exception.getClass().getSimpleName());
        return errorResponse(ErrorCode.DATA_CONFLICT);
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
