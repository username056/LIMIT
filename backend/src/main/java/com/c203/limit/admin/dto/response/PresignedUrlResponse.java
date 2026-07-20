package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PresignedUrlResponse", description = "S3 Presigned URL 응답")
public class PresignedUrlResponse {

    @Schema(description = "증빙 문서 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long documentId;

    @Schema(description = "제한 시간 다운로드 URL", example = "https://...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String downloadUrl;

    @Schema(description = "URL 만료 시각", example = "2026-07-16T16:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime expiresAt;
}
