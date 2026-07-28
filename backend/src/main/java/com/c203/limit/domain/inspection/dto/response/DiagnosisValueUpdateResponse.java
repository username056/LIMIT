package com.c203.limit.domain.inspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DiagnosisValueUpdateResponse", description = "진단값 확정/수정 결과")
public class DiagnosisValueUpdateResponse {

    @Schema(example = "2")
    private final Long itemId;

    @Schema(example = "CPU")
    private final String fieldName;

    @Schema(description = "이 수정 직전까지 ocr_result/dxdiag_result/battery_report_result에 있던 값", example = "13th Gen Intel(R) Core(TM) i7-13700H (20 CPUs), ~2.4GHz")
    private final String originalValue;

    @Schema(example = "13th Gen Intel(R) Core(TM) i7-13700H")
    private final String confirmedValue;

    @Schema(example = "2026-07-28T10:15:00")
    private final LocalDateTime updatedAt;
}
