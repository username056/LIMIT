package com.c203.limit.domain.inspection.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * correctField()가 battery_report_result의 5개 컬럼에 정확히 매핑되는지, CYCLE_COUNT/CAPACITY_RATIO의
 * 문자열→숫자 변환과 null/blank 가드가 맞는지, dxdiag 전용 필드는 거부하는지 확인한다.
 */
class BatteryReportResultTests {

    private BatteryReportResult newResult() {
        return BatteryReportResult.builder()
                .evidenceId(1L)
                .parserVersion("battery-dom-v1")
                .parseStatus(ParseStatus.PARTIAL)
                .parsedAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                .build();
    }

    @Test
    void correctFieldOverwritesPlainStringColumns() {
        BatteryReportResult result = newResult();

        result.correctField(DiagnosisFieldName.DESIGN_CAPACITY, "50000 mWh");
        result.correctField(DiagnosisFieldName.FULL_CHARGE_CAPACITY, "48000 mWh");
        result.correctField(DiagnosisFieldName.BATTERY_MANUFACTURER, "LGC");

        assertThat(result.getDesignCapacity()).isEqualTo("50000 mWh");
        assertThat(result.getFullChargeCapacity()).isEqualTo("48000 mWh");
        assertThat(result.getBatteryManufacturer()).isEqualTo("LGC");
    }

    @Test
    void correctFieldParsesCycleCountAndCapacityRatioIntoNumericTypes() {
        BatteryReportResult result = newResult();

        result.correctField(DiagnosisFieldName.CYCLE_COUNT, "120");
        result.correctField(DiagnosisFieldName.CAPACITY_RATIO, "96.00");

        assertThat(result.getCycleCount()).isEqualTo(120);
        assertThat(result.getCapacityRatio()).isEqualByComparingTo("96.00");
    }

    @Test
    void correctFieldTrimsWhitespaceBeforeParsingNumericFields() {
        BatteryReportResult result = newResult();

        result.correctField(DiagnosisFieldName.CYCLE_COUNT, " 120 ");
        result.correctField(DiagnosisFieldName.CAPACITY_RATIO, " 96.00 ");

        assertThat(result.getCycleCount()).isEqualTo(120);
        assertThat(result.getCapacityRatio()).isEqualByComparingTo("96.00");
    }

    @Test
    void correctFieldTreatsNullOrBlankAsClearingTheNumericColumns() {
        BatteryReportResult result = newResult();
        result.correctField(DiagnosisFieldName.CYCLE_COUNT, "120");
        result.correctField(DiagnosisFieldName.CAPACITY_RATIO, "96.00");

        result.correctField(DiagnosisFieldName.CYCLE_COUNT, "  ");
        result.correctField(DiagnosisFieldName.CAPACITY_RATIO, null);

        assertThat(result.getCycleCount()).isNull();
        assertThat(result.getCapacityRatio()).isNull();
    }

    @Test
    void correctFieldPropagatesNumberFormatExceptionForInvalidCycleCount() {
        // correctField에는 try-catch가 없으므로 잘못된 숫자 문자열은 그대로 예외로 전파된다.
        BatteryReportResult result = newResult();

        assertThatThrownBy(() -> result.correctField(DiagnosisFieldName.CYCLE_COUNT, "not-a-number"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void correctingOneFieldDoesNotTouchOthers() {
        BatteryReportResult result = newResult();
        result.correctField(DiagnosisFieldName.BATTERY_MANUFACTURER, "LGC");

        result.correctField(DiagnosisFieldName.DESIGN_CAPACITY, "50000 mWh");

        assertThat(result.getBatteryManufacturer()).isEqualTo("LGC");
        assertThat(result.getDesignCapacity()).isEqualTo("50000 mWh");
    }

    @Test
    void correctingDoesNotRecomputeParseStatus() {
        BatteryReportResult result = newResult();

        result.correctField(DiagnosisFieldName.DESIGN_CAPACITY, "50000 mWh");

        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.PARTIAL);
    }

    @Test
    void rejectsFieldNamesThatHaveNoBatteryReportColumn() {
        // MODEL_NAME 등은 dxdiag 전용 필드라 battery_report_result에 대응 컬럼이 없다.
        BatteryReportResult result = newResult();

        assertThatThrownBy(() -> result.correctField(DiagnosisFieldName.MODEL_NAME, "960XFH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MODEL_NAME");
    }
}
