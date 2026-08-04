package com.c203.limit.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProfileImageResponse", description = "프로필 사진 변경 결과")
public record ProfileImageResponse(
        @Schema(description = "바뀐 프로필 사진 주소. 사진을 내렸으면 null") String profileImageUrl) {}
