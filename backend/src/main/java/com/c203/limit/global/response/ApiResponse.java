package com.c203.limit.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiResponse", description = "성공 응답 공통 래퍼")
public record ApiResponse<T>(T data, Object meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }
}
