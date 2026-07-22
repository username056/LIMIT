package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ApproveSellerApplicationResponse", description = "판매자 신청 승인 결과")
public class ApproveSellerApplicationResponse {

    @Schema(description = "신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long applicationId;

    @Schema(description = "승인 상태", example = "APPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "생성된 판매자 프로필 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sellerProfileId;

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "판매자 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerStatus;

    @Schema(description = "승인 후 회원 역할", example = "[BUYER, SELLER]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;

    @Schema(description = "승인 시각", example = "2026-07-16T16:30:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime reviewedAt;
}
