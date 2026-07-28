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
        return foundFieldCount() == 4;
    }

    /** design_capacity, full_charge_capacity, battery_manufacturer, cycle_count 중 인식에 성공한 개수. */
    public int foundFieldCount() {
        int count = 0;
        if (batteryManufacturer != null) count++;
        if (designCapacity != null) count++;
        if (fullChargeCapacity != null) count++;
        if (cycleCount != null) count++;
        return count;
    }
}
