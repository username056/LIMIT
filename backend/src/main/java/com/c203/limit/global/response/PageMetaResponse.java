package com.c203.limit.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PageMetaResponse", description = "목록 응답 페이징 메타데이터")
public record PageMetaResponse(
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "42") long totalElements,
        @Schema(example = "3") int totalPages,
        @Schema(example = "true") boolean hasNext
) {
}
