package com.c203.limit.admin.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminMemberSearchRequest", description = "관리자 회원 검색 조건")
public class AdminMemberSearchRequest {

    @Schema(description = "페이지 번호", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "이메일 검색어", example = "user@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String email;

    @Schema(description = "닉네임 검색어", example = "openrunner", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String nickname;

    @Schema(description = "회원 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String status;

    @Schema(description = "회원 역할", example = "SELLER", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String role;

    @Schema(description = "가입일 시작", example = "2026-07-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate createdFrom;

    @Schema(description = "가입일 종료", example = "2026-07-31", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate createdTo;

    @Schema(description = "정렬 조건", example = "createdAt,desc", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String sort;
}
