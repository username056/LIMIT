package com.c203.limit.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateMemberResponse", description = "회원 정보 수정 결과")
public class UpdateMemberResponse {

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(
            description = "수정된 닉네임",
            example = "newNickname",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(
            description = "마스킹된 연락처",
            example = "010****5432",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String phone;

    @Schema(
            description = "수정 시각",
            example = "2026-07-16T11:10:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final LocalDateTime updatedAt;
}
