package com.c203.limit.domain.product.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "RecaptureRequestResponse", description = "체크리스트 항목 재촬영 요청 결과")
public class RecaptureRequestResponse {

    @Schema(example = "8101")
    private final Long recaptureRequestId;

    @Schema(example = "7002")
    private final Long checklistItemId;

    @Schema(example = "9002")
    private final Long requestedEvidenceId;

    @Schema(example = "SCREEN_NOT_VISIBLE")
    private final String reasonCode;

    @Schema(example = "화면 오른쪽 위가 보이지 않습니다.")
    private final String reason;

    @Schema(example = "REQUESTED")
    private final String status;

    @Schema(example = "2026-07-22T13:10:00+09:00")
    private final OffsetDateTime requestedAt;
}
