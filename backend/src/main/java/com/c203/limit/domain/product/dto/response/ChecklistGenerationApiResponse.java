package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChecklistGenerationApiResponse", description = "체크리스트 생성 공통 응답")
public record ChecklistGenerationApiResponse(
        ChecklistGenerationResponse data, @Schema(nullable = true) Object meta) {}
