package com.c203.limit.domain.product.moderation.dto.request;

import com.c203.limit.domain.product.moderation.entity.RestorationDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AdminRestorationDecisionRequest", description = "관리자 상품 복구 심사 요청")
public record AdminRestorationDecisionRequest(
        @NotNull RestorationDecision decision,
        @NotBlank @Size(max = 1000) String note) {}
