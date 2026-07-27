package com.c203.limit.domain.inspection.dto.response;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.DxdiagParseResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DxdiagResultResponse", description = "DxDiag.xml 파싱 결과")
public class DxdiagResultResponse {

    @Schema(example = "1")
    private final Long dxdiagResultId;

    @Schema(example = "9004")
    private final Long evidenceId;

    @Schema(description = "제조사 (dxdiag_result에는 저장되지 않고 응답에만 포함)", example = "SAMSUNG ELECTRONICS CO., LTD.")
    private final String manufacturer;

    @Schema(description = "모델명 (dxdiag_result에는 저장되지 않고 응답에만 포함)", example = "950XDB/951XDB/950XDY")
    private final String model;

    @Schema(description = "OS 버전 (dxdiag_result에는 저장되지 않고 응답에만 포함)", example = "Windows 10 Pro 64-bit")
    private final String osVersion;

    @Schema(example = "11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz")
    private final String cpu;

    @Schema(example = "16384MB RAM")
    private final String memory;

    @Schema(example = "Intel(R) Iris(R) Xe Graphics")
    private final String gpu;

    @Schema(example = "8156 MB")
    private final String gpuMemory;

    @Schema(example = "27.20.100.9415")
    private final String driverVersion;

    @Schema(example = "스피커(Realtek(R) Audio)")
    private final String soundDevice;

    @Schema(example = "dxdiag-dom-v1")
    private final String parserVersion;

    @Schema(description = "SUCCESS: 전체 인식, PARTIAL: 일부 인식, FAILED: 해석 실패", example = "SUCCESS")
    private final ParseStatus parseStatus;

    @Schema(example = "2026-07-23T20:55:12")
    private final LocalDateTime parsedAt;

    public static DxdiagResultResponse from(DxdiagResult entity, DxdiagParseResult parsed) {
        return new DxdiagResultResponse(
                entity.getId(),
                entity.getEvidenceId(),
                parsed == null ? null : parsed.manufacturer(),
                parsed == null ? null : parsed.model(),
                parsed == null ? null : parsed.osVersion(),
                entity.getCpu(),
                entity.getMemory(),
                entity.getGpu(),
                entity.getGpuMemory(),
                entity.getDriverVersion(),
                entity.getSoundDevice(),
                entity.getParserVersion(),
                entity.getParseStatus(),
                entity.getParsedAt());
    }
}
