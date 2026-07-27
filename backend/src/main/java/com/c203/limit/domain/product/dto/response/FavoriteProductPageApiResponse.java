package com.c203.limit.domain.product.dto.response;

import com.c203.limit.global.response.PageMetaResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "FavoriteProductPageApiResponse", description = "관심상품 목록 공통 응답")
public record FavoriteProductPageApiResponse(
        List<FavoriteProductResponse> data,
        PageMetaResponse meta) {}
