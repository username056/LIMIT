package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SignupRequest", description = "이메일 회원가입 요청")
public class SignupRequest {

    @Schema(description = "로그인 이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(description = "8자 이상 영문·숫자 조합 비밀번호", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String password;

    @Schema(description = "2~20자의 중복 불가 닉네임", example = "openrunner", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(description = "회원 연락처", example = "01012345678", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String phone;
}
