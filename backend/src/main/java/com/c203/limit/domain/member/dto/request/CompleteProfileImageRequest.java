package com.c203.limit.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CompleteProfileImageRequest {
    /** 업로드 URL을 받을 때 서버가 정해 준 오브젝트 키를 그대로 돌려준다. */
    @NotBlank private final String objectKey;
}
