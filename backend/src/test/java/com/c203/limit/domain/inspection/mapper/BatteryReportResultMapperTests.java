package com.c203.limit.domain.inspection.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.BatteryReportParseResult;
import com.c203.limit.domain.inspection.dto.response.BatteryReportResultResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * toEntity()의 상태 판정은 foundFieldCount()(4개 필드 기준, capacityRatio 제외)로 갈리므로
 * capacityRatio 유무는 SUCCESS/PARTIAL/FAILED 판정에 영향을 주지 않는다는 것까지 확인한다.
 */
class BatteryReportResultMapperTests {

    private static final LocalDateTime PARSED_AT = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void marksSuccessWhenAllFourTrackedFieldsArePresent() {
        BatteryReportParseResult parsed = new BatteryReportParseResult(
                "LGC", "50000 mWh", "48000 mWh", 120, new BigDecimal("96.00"));

        BatteryReportResult entity = BatteryReportResultMapper.toEntity(1L, parsed, "battery-dom-v1", PARSED_AT);

        assertThat(entity.getParseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(entity.getEvidenceId()).isEqualTo(1L);
        assertThat(entity.getBatteryManufacturer()).isEqualTo("LGC");
        assertThat(entity.getParserVersion()).isEqualTo("battery-dom-v1");
        assertThat(entity.getParsedAt()).isEqualTo(PARSED_AT);
    }

    @Test
    void marksSuccessEvenWithoutCapacityRatioBecauseItIsNotTracked() {
        // capacityRatio가 없어도 나머지 4개 필드만 있으면 SUCCESS다.
        BatteryReportParseResult parsed = new BatteryReportParseResult(
                "LGC", "50000 mWh", "48000 mWh", 120, null);

        BatteryReportResult entity = BatteryReportResultMapper.toEntity(1L, parsed, "battery-dom-v1", PARSED_AT);

        assertThat(entity.getParseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(entity.getCapacityRatio()).isNull();
    }

    @Test
    void marksPartialWhenSomeButNotAllTrackedFieldsAreMissing() {
        BatteryReportParseResult parsed = new BatteryReportParseResult(
                "LGC", null, "48000 mWh", null, new BigDecimal("96.00"));

        BatteryReportResult entity = BatteryReportResultMapper.toEntity(1L, parsed, "battery-dom-v1", PARSED_AT);

        assertThat(entity.getParseStatus()).isEqualTo(ParseStatus.PARTIAL);
    }

    @Test
    void marksFailedWhenEveryTrackedFieldIsMissing() {
        BatteryReportParseResult parsed = new BatteryReportParseResult(null, null, null, null, null);

        BatteryReportResult entity = BatteryReportResultMapper.toEntity(1L, parsed, "battery-dom-v1", PARSED_AT);

        assertThat(entity.getParseStatus()).isEqualTo(ParseStatus.FAILED);
    }

    @Test
    void toResponseCarriesAllFieldsFromTheEntity() {
        BatteryReportParseResult parsed = new BatteryReportParseResult(
                "LGC", "50000 mWh", "48000 mWh", 120, new BigDecimal("96.00"));
        BatteryReportResult entity = BatteryReportResultMapper.toEntity(1L, parsed, "battery-dom-v1", PARSED_AT);

        BatteryReportResultResponse response = BatteryReportResultMapper.toResponse(entity);

        assertThat(response.getDesignCapacity()).isEqualTo("50000 mWh");
        assertThat(response.getFullChargeCapacity()).isEqualTo("48000 mWh");
        assertThat(response.getCycleCount()).isEqualTo(120);
        assertThat(response.getBatteryManufacturer()).isEqualTo("LGC");
        assertThat(response.getCapacityRatio()).isEqualByComparingTo("96.00");
        assertThat(response.getParserVersion()).isEqualTo("battery-dom-v1");
        assertThat(response.getStatus()).isEqualTo(ParseStatus.SUCCESS);
    }
}
