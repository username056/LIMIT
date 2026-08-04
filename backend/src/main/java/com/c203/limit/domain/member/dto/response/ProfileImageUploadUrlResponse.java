package com.c203.limit.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;

@Schema(name = "ProfileImageUploadUrlResponse", description = "프로필 사진 업로드용 presigned URL")
public record ProfileImageUploadUrlResponse(
        @Schema(description = "완료 통보 때 그대로 돌려줄 오브젝트 키") String objectKey,
        @Schema(description = "이 주소로 PUT 한 번만 보낼 수 있다") String presignedUrl,
        @Schema(description = "URL 만료 시각") OffsetDateTime expiresAt,
        @Schema(description = "PUT 할 때 반드시 함께 보내야 하는 헤더") Map<String, String> requiredHeaders) {}
