package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * foundFieldCount()/isComplete()는 capacityRatio를 세지 않고 나머지 4개 필드만 센다는 점이
 * DxdiagParseResult와 다르다 — 그 비대칭을 명시적으로 확인한다.
 */
class BatteryReportParseResultTests {

    @Test
    void isCompleteWhenAllFourTrackedFieldsArePresent() {
        BatteryReportParseResult result = new BatteryReportParseResult(
                "LGC", "50000 mWh", "48000 mWh", 120, new BigDecimal("96.00"));

        assertThat(result.foundFieldCount()).isEqualTo(4);
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void capacityRatioDoesNotCountTowardFoundFieldCount() {
        // capacityRatio는 나머지 4개가 다 있어도 필드 카운트에서 빠진다.
        BatteryReportParseResult withoutRatio = new BatteryReportParseResult(
                "LGC", "50000 mWh", "48000 mWh", 120, null);
        BatteryReportParseResult onlyRatio = new BatteryReportParseResult(
                null, null, null, null, new BigDecimal("96.00"));

        assertThat(withoutRatio.foundFieldCount()).isEqualTo(4);
        assertThat(withoutRatio.isComplete()).isTrue();
        assertThat(onlyRatio.foundFieldCount()).isZero();
        assertThat(onlyRatio.isComplete()).isFalse();
    }

    @Test
    void countsEachMissingFieldIndividually() {
        BatteryReportParseResult result = new BatteryReportParseResult(
                "LGC", null, "48000 mWh", null, null);

        assertThat(result.foundFieldCount()).isEqualTo(2);
        assertThat(result.isComplete()).isFalse();
    }
}
