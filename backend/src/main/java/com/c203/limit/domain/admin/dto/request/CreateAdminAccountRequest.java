package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateAdminAccountRequest", description = "관리자 계정 생성 요청")
public record CreateAdminAccountRequest(
        @Schema(example = "operator@limit.local") String email,
        @Schema(description = "12자 이상 초기 비밀번호") String password,
        @Schema(example = "운영자") String name,
        @Schema(example = "OPERATOR") String role) {}
