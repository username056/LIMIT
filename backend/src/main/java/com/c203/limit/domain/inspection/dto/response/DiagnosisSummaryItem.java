package com.c203.limit.domain.inspection.dto.response;

import com.c203.limit.domain.inspection.enums.DiagnosisSummaryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DiagnosisSummaryItem", description = "구매자에게 보여줄 필드 하나의 진단 요약")
public record DiagnosisSummaryItem(
        @Schema(example = "CPU") String fieldName,
        @Schema(
                        description = "근거가 된 원본 파일 URL (근거가 없으면 null)",
                        example = "https://cdn.example.com/evidence/2.txt")
                String originalFileUrl,
        @Schema(
                        description = "ocr_result/dxdiag_result/battery_report_result에 있는 현재 값"
                                + " (판매자가 고쳤다면 고친 값, 아니면 자동 추출값)",
                        example = "13th Gen Intel(R) Core(TM) i7-13700H (20 CPUs), ~2.4GHz")
                String value,
        @Schema(description = "AVAILABLE: 값 있음, EXTRACTION_FAILED: 값 없음", example = "AVAILABLE")
                DiagnosisSummaryStatus status) {}
