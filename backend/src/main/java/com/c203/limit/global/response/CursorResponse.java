package com.c203.limit.global.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CursorResponse", description = "커서 기반 목록 응답")
public record CursorResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> content,
        @Schema(description = "다음 조회 커서", nullable = true) String nextCursor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasNext) {
}
