package com.c203.limit.global.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PageResponse", description = "페이징 응답 공통 DTO")
public class PageResponse<T> {

    @Schema(description = "현재 페이지 데이터", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<T> content;

    @Schema(description = "0부터 시작하는 현재 페이지", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "전체 데이터 수", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long totalElements;

    @Schema(description = "전체 페이지 수", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int totalPages;

    @Schema(description = "다음 페이지 존재 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean hasNext;
}
