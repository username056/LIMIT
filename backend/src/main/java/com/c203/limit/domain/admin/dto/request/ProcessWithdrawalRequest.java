package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ProcessWithdrawalRequest", description = "관리자 탈퇴 요청 처리")
public class ProcessWithdrawalRequest {

    @Schema(description = "처리 결정", example = "COMPLETE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String decision;

    @Schema(description = "BLOCK 결정 시 제한 사유", example = "진행 중인 분쟁이 존재합니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String blockedReason;
}
