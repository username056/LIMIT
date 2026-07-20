package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RejectSellerApplicationResponse", description = "판매자 신청 거절 결과")
public class RejectSellerApplicationResponse {

    @Schema(description = "신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long applicationId;

    @Schema(description = "거절 상태", example = "REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "거절 사유", example = "사업자 증빙의 식별번호를 확인할 수 없습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String rejectionReason;

    @Schema(description = "거절 시각", example = "2026-07-16T16:30:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime reviewedAt;
}
