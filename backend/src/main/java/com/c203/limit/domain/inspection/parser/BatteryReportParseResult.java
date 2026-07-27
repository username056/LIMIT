package com.c203.limit.domain.inspection.parser;

import java.math.BigDecimal;

/** Windows powercfg /batteryreport HTML에서 추출한 배터리 수명 정보. */
public record BatteryReportParseResult(
        String batteryManufacturer,
        String designCapacity,
        String fullChargeCapacity,
        Integer cycleCount,
        BigDecimal capacityRatio) {

    public boolean isComplete() {
        return batteryManufacturer != null
                && designCapacity != null
                && fullChargeCapacity != null
                && cycleCount != null
                && capacityRatio != null;
    }
}
