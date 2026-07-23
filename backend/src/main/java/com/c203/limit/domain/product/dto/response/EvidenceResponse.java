package com.c203.limit.domain.product.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "EvidenceResponse", description = "항목별 증거 및 재촬영 시도 이력")
public class EvidenceResponse {

    @Schema(example = "9003")
    private final Long evidenceId;

    @Schema(example = "7002")
    private final Long checklistItemId;

    @Schema(example = "VIDEO")
    private final String evidenceType;

    @Schema(example = "2")
    private final Integer attemptNo;

    @Schema(example = "true")
    private final boolean isLatest;

    @Schema(example = "https://cdn.example.com/evidence/9003.mp4")
    private final String mediaUrl;

    @Schema(example = "READY")
    private final String processingStatus;

    @Schema(example = "NONE")
    private final String buyerConfirmationStatus;

    @Schema(example = "2026-07-22T12:01:00+09:00")
    private final OffsetDateTime capturedAt;

    @Schema(example = "2026-07-22T12:03:00+09:00")
    private final OffsetDateTime uploadedAt;
}
