package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FavoriteStatusResponse", description = "현재 회원의 관심상품 등록 상태")
public record FavoriteStatusResponse(
        @Schema(example = "true") boolean favorite) {}
