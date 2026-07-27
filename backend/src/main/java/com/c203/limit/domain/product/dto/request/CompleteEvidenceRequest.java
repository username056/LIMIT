package com.c203.limit.domain.product.dto.request;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "CompleteEvidenceRequest", description = "증거 파일 업로드 완료 요청")
public class CompleteEvidenceRequest {

    @Schema(description = "업로드 세션 ID", example = "upl_01")
    @NotBlank
    private final String uploadId;

    @Schema(description = "클라이언트 촬영 시각", example = "2026-07-22T12:01:00+09:00")
    private final OffsetDateTime capturedAt;
}
