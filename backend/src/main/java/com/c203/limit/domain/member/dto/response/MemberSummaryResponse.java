package com.c203.limit.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "MemberSummaryResponse", description = "여러 API에서 재사용하는 회원 요약")
public class MemberSummaryResponse {

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(
            description = "닉네임",
            example = "openrunner",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(
            description = "권한 목록",
            example = "[MEMBER]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;

    @Schema(
            description = "판매자 상태. 판매자 등록 전에는 null",
            example = "ACTIVE",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String sellerStatus;

    @Schema(
            description = "프로필 사진 주소. 올리지 않았으면 null",
            example = "https://cdn.example.com/members/1/profile/abc.webp",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String profileImageUrl;
}
