package com.c203.limit.domain.inspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ProductDiagnosisSummaryResponse", description = "구매자가 상품 상세에서 보는 검수 진단 최종 요약")
public record ProductDiagnosisSummaryResponse(
        @Schema(example = "1") Long productId,
        List<DiagnosisSummaryItem> items,
        @Schema(example = "자동 추출값은 참고 정보이며 상품의 정상 여부를 보증하지 않습니다.") String disclaimer) {}
