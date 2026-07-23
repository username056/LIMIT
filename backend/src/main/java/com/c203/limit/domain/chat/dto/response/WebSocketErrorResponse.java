package com.c203.limit.domain.chat.dto.response;

import java.util.List;

import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.ApiErrorResponse.FieldError;

public record WebSocketErrorResponse(ErrorDetail error, String traceId) {

    public record ErrorDetail(String code, String message, List<FieldError> fieldErrors) {}

    public static WebSocketErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new WebSocketErrorResponse(
                new ErrorDetail(errorCode.getCode(), message, List.of()), traceId);
    }
}
