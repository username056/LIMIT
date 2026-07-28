package com.c203.limit.domain.inspection.mapper;

import com.c203.limit.domain.inspection.dto.response.DxdiagResultResponse;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.DxdiagParseResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** DxdiagParseResult(DTO) ↔ DxdiagResult(Entity) ↔ DxdiagResultResponse(DTO) 변환. */
public final class DxdiagResultMapper {

    private static final int TRACKED_FIELD_COUNT = 6;

    private DxdiagResultMapper() {}

    public static DxdiagResult toEntity(
            Long evidenceId, DxdiagParseResult parsed, String parserVersion, LocalDateTime parsedAt) {
        return DxdiagResult.builder()
                .evidenceId(evidenceId)
                .cpu(parsed.cpu())
                .memory(parsed.memory())
                .gpu(parsed.gpu())
                .gpuMemory(parsed.gpuMemory())
                .driverVersion(parsed.driverVersion())
                .soundDevice(parsed.soundDevice())
                .parserVersion(parserVersion)
                .parseStatus(resolveStatus(parsed))
                .parsedAt(parsedAt)
                .build();
    }

    public static DxdiagResultResponse toResponse(DxdiagResult entity) {
        return new DxdiagResultResponse(
                entity.getId(),
                entity.getCpu(),
                entity.getMemory(),
                entity.getGpu(),
                entity.getGpuMemory(),
                entity.getDriverVersion(),
                entity.getSoundDevice(),
                entity.getParserVersion(),
                entity.getParseStatus(),
                missingFields(entity));
    }

    private static ParseStatus resolveStatus(DxdiagParseResult parsed) {
        int missing = parsed.missingFields().size();
        if (missing == 0) {
            return ParseStatus.SUCCESS;
        }
        return missing == TRACKED_FIELD_COUNT ? ParseStatus.FAILED : ParseStatus.PARTIAL;
    }

    private static List<String> missingFields(DxdiagResult entity) {
        List<String> missing = new ArrayList<>();
        if (entity.getCpu() == null) missing.add("cpu");
        if (entity.getMemory() == null) missing.add("memory");
        if (entity.getGpu() == null) missing.add("gpu");
        if (entity.getGpuMemory() == null) missing.add("gpuMemory");
        if (entity.getDriverVersion() == null) missing.add("driverVersion");
        if (entity.getSoundDevice() == null) missing.add("soundDevice");
        return missing;
    }
}
