package com.c203.limit.global.response;

import java.util.List;

import com.c203.limit.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 오류 응답 공통 DTO. 컨벤션의 실패 응답 계약을 따른다.
 * {@code {"error":{"code","message","fieldErrors"},"traceId"}}
 */
@Schema(name = "ApiErrorResponse", description = "실패 응답 공통 래퍼")
public record ApiErrorResponse(ErrorDetail error, String traceId) {

    @Schema(name = "ApiErrorDetailResponse", description = "오류 상세")
    public record ErrorDetail(String code, String message, List<FieldError> fieldErrors) {}

    @Schema(name = "FieldErrorResponse", description = "필드 검증 오류 상세")
    public record FieldError(String field, String reason) {}

    public static ApiErrorResponse of(ErrorCode errorCode, String traceId) {
        return of(errorCode, errorCode.getMessage(), List.of(), traceId);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors, String traceId) {
        return of(errorCode, errorCode.getMessage(), fieldErrors, traceId);
    }

    public static ApiErrorResponse of(
            ErrorCode errorCode, String message, List<FieldError> fieldErrors, String traceId) {
        return new ApiErrorResponse(new ErrorDetail(errorCode.getCode(), message, fieldErrors), traceId);
    }
}
