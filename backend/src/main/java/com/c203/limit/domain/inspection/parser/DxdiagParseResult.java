package com.c203.limit.domain.inspection.parser;

import java.util.ArrayList;
import java.util.List;

/** DxDiag 진단 파일(txt/xml)에서 추출한, dxdiag_result 테이블 컬럼과 1:1로 대응하는 값. */
public record DxdiagParseResult(
        String cpu,
        String memory,
        String gpu,
        String gpuMemory,
        String driverVersion,
        String soundDevice) {

    public boolean isComplete() {
        return missingFields().isEmpty();
    }

    /** cpu, memory, gpu, gpuMemory, driverVersion, soundDevice 중 인식하지 못한 필드명. */
    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (cpu == null) missing.add("cpu");
        if (memory == null) missing.add("memory");
        if (gpu == null) missing.add("gpu");
        if (gpuMemory == null) missing.add("gpuMemory");
        if (driverVersion == null) missing.add("driverVersion");
        if (soundDevice == null) missing.add("soundDevice");
        return missing;
    }
}
