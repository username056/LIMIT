package com.c203.limit.domain.inspection.mapper;

import com.c203.limit.domain.inspection.dto.response.BatteryReportResultResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.BatteryReportParseResult;
import java.time.LocalDateTime;

/** BatteryReportParseResult(DTO) ↔ BatteryReportResult(Entity) ↔ BatteryReportResultResponse(DTO) 변환. */
public final class BatteryReportResultMapper {

    private BatteryReportResultMapper() {}

    public static BatteryReportResult toEntity(
            Long evidenceId, BatteryReportParseResult parsed, String parserVersion, LocalDateTime parsedAt) {
        return BatteryReportResult.builder()
                .evidenceId(evidenceId)
                .batteryManufacturer(parsed.batteryManufacturer())
                .designCapacity(parsed.designCapacity())
                .fullChargeCapacity(parsed.fullChargeCapacity())
                .cycleCount(parsed.cycleCount())
                .capacityRatio(parsed.capacityRatio())
                .parserVersion(parserVersion)
                .parseStatus(resolveStatus(parsed))
                .parsedAt(parsedAt)
                .build();
    }

    public static BatteryReportResultResponse toResponse(BatteryReportResult entity) {
        return new BatteryReportResultResponse(
                entity.getId(),
                entity.getDesignCapacity(),
                entity.getFullChargeCapacity(),
                entity.getCycleCount(),
                entity.getBatteryManufacturer(),
                entity.getCapacityRatio(),
                entity.getParserVersion(),
                entity.getParseStatus());
    }

    private static ParseStatus resolveStatus(BatteryReportParseResult parsed) {
        int found = parsed.foundFieldCount();
        if (found == 4) {
            return ParseStatus.SUCCESS;
        }
        return found == 0 ? ParseStatus.FAILED : ParseStatus.PARTIAL;
    }
}
