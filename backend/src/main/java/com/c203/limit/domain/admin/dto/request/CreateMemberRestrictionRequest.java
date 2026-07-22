package com.c203.limit.domain.admin.dto.request;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateMemberRestrictionRequest", description = "회원 이용 제한 등록 요청")
public class CreateMemberRestrictionRequest {

    @Schema(description = "제한 기능 범위", example = "PURCHASE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String restrictionType;

    @Schema(description = "정책상 제재 사유 코드", example = "MACRO_USE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reasonCode;

    @Schema(description = "구체적인 제재 근거", example = "비정상 반복 요청 탐지", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reasonDetail;

    @Schema(description = "제재 시작 시각", example = "2026-07-16T14:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime startsAt;

    @Schema(description = "제재 종료 시각", example = "2026-07-23T14:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime endsAt;
}
