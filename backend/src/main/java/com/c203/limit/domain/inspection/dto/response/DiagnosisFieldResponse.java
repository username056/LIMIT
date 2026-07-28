package com.c203.limit.domain.inspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DiagnosisFieldResponse", description = "필드 하나에 대한 OCR/진단파일 취합 결과")
public class DiagnosisFieldResponse {

    @Schema(example = "CPU")
    private final String fieldName;

    @Schema(description = "OCR 스크린샷에서 인식한 값 (없으면 null)", example = "11th Gen Intel(R) Core(TM) i7-1165G7")
    private final String ocrValue;

    @Schema(description = "진단 파일(dxdiag/배터리 리포트)에서 파싱한 값 (없으면 null)", example = "11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz")
    private final String fileParseValue;

    @Schema(description = "ocrValue와 fileParseValue가 둘 다 있고 서로 다르면 true", example = "false")
    private final boolean conflict;

    @Schema(description = "검수자가 확정한 값 (아직 확정 전이면 null)", example = "null")
    private final String confirmedValue;
}
