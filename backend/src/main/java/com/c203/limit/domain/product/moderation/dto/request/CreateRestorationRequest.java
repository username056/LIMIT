package com.c203.limit.domain.product.moderation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateRestorationRequest", description = "판매 중지 상품 복구 신청")
public record CreateRestorationRequest(@Size(max = 1000) String requestNote) {}
