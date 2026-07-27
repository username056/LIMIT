package com.c203.limit.domain.product.dto.response;

import com.c203.limit.global.response.PageMetaResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ProductSummaryPageApiResponse", description = "공개 상품 목록 공통 응답")
public record ProductSummaryPageApiResponse(
        List<ProductSummaryResponse> data,
        PageMetaResponse meta) {}
