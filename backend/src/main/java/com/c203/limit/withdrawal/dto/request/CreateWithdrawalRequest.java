package com.c203.limit.withdrawal.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateWithdrawalRequest", description = "회원 탈퇴 요청")
public class CreateWithdrawalRequest {

    @Schema(description = "LOCAL 회원 본인 확인용 비밀번호", example = "Password123!", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String password;

    @Schema(description = "탈퇴 사유", example = "서비스 이용 빈도 감소", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String withdrawalReason;
}
