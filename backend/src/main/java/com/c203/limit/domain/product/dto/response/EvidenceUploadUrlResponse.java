package com.c203.limit.domain.product.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "EvidenceUploadUrlResponse", description = "체크리스트 증거 Presigned URL")
public class EvidenceUploadUrlResponse {

    @Schema(example = "upl_01")
    private final String uploadId;

    @Schema(example = "products/1001/checklist/7002/attempt-2.mp4")
    private final String storageKey;

    @Schema(example = "https://storage.example.com/presigned")
    private final String presignedUrl;

    @Schema(example = "2026-07-22T12:15:00+09:00")
    private final OffsetDateTime expiresAt;

    private final Map<String, String> requiredHeaders;
}
