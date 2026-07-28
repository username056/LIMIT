package com.c203.limit.domain.inspection.dto.response;

import com.c203.limit.domain.inspection.enums.ParseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "BatteryReportResultResponse", description = "배터리 리포트 파싱 결과")
public class BatteryReportResultResponse {

    @Schema(example = "1")
    private final Long batteryReportResultId;

    @Schema(example = "67,010 mWh")
    private final String designCapacity;

    @Schema(example = "55,584 mWh")
    private final String fullChargeCapacity;

    @Schema(example = "418")
    private final Integer cycleCount;

    @Schema(example = "SAMSUNG Electronics")
    private final String batteryManufacturer;

    @Schema(description = "완전충전용량/설계용량 * 100", example = "82.95")
    private final BigDecimal capacityRatio;

    @Schema(example = "battery-report-v1")
    private final String parserVersion;

    @Schema(description = "SUCCESS: 전체 인식, PARTIAL: 일부 인식, FAILED: 해석 실패", example = "SUCCESS")
    private final ParseStatus status;
}
