package com.c203.limit.domain.product.moderation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResolveRiskSignalRequest", description = "이상 활동 신호 검토 완료 요청")
public record ResolveRiskSignalRequest(@NotBlank @Size(max = 500) String note) {}
