package com.c203.limit.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateMemberRequest", description = "회원 정보 부분 수정 요청")
public class UpdateMemberRequest {

    @Schema(description = "변경할 닉네임", example = "newNickname", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String nickname;

    @Schema(description = "변경할 연락처", example = "01098765432", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String phone;
}
