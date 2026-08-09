package com.c203.limit.domain.product.moderation.dto.request;

import com.c203.limit.domain.product.moderation.entity.ModerationDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AdminModerationDecisionRequest", description = "관리자 신고 처리 요청")
public record AdminModerationDecisionRequest(
        @NotNull ModerationDecision decision,
        @NotBlank @Size(max = 1000) String note) {}
