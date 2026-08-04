package com.c203.limit.domain.inspection.dto.response;

import com.c203.limit.domain.inspection.enums.ParseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DxdiagResultResponse", description = "DxDiag 파싱 결과")
public class DxdiagResultResponse {

    @Schema(example = "1")
    private final Long dxdiagResultId;

    @Schema(example = "960XFH")
    private final String modelName;

    @Schema(example = "Windows 11 Enterprise 64-bit (10.0, Build 26200)")
    private final String osVersion;

    @Schema(example = "975.7 GB")
    private final String storageCapacity;

    @Schema(example = "11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz (8 CPUs), ~2.8GHz")
    private final String cpu;

    @Schema(example = "16384 MB RAM")
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
    private final ParseStatus status;

    @Schema(description = "인식하지 못한 필드명", example = "[\"gpuMemory\", \"soundDevice\"]")
    private final List<String> missingFields;
}
