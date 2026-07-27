package com.c203.limit.domain.inspection.parser;

/** DxDiag.xml에서 추출한 값. manufacturer/model/osVersion은 dxdiag_result에 저장되지 않고 응답에만 담긴다. */
public record DxdiagParseResult(
        String manufacturer,
        String model,
        String osVersion,
        String cpu,
        String memory,
        String gpu,
        String gpuMemory,
        String driverVersion,
        String soundDevice) {

    public boolean isComplete() {
        return manufacturer != null
                && model != null
                && osVersion != null
                && cpu != null
                && memory != null
                && gpu != null
                && gpuMemory != null
                && driverVersion != null
                && soundDevice != null;
    }
}
