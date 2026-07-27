package com.c203.limit.domain.inspection.enums;

/**
 * OCR 스크린샷 결과와 진단 파일(dxdiag/배터리 리포트) 파싱 결과를 같은 필드 기준으로 취합하기 위한 공통 필드명.
 * CPU/RAM/GPU/MODEL_NAME/OS_VERSION/STORAGE_CAPACITY는 {@link OcrFieldType}과 대응되고, 나머지는
 * 진단 파일에만 존재해 OCR 값이 없다.
 */
public enum DiagnosisFieldName {
    CPU,
    RAM,
    GPU,
    MODEL_NAME,
    OS_VERSION,
    STORAGE_CAPACITY,
    GPU_MEMORY,
    DRIVER_VERSION,
    SOUND_DEVICE,
    DESIGN_CAPACITY,
    FULL_CHARGE_CAPACITY,
    CYCLE_COUNT,
    BATTERY_MANUFACTURER,
    CAPACITY_RATIO;

    public static DiagnosisFieldName fromOcrFieldType(OcrFieldType fieldType) {
        return switch (fieldType) {
            case CPU -> CPU;
            case RAM -> RAM;
            case GPU -> GPU;
            case MODEL_NAME -> MODEL_NAME;
            case OS_VERSION -> OS_VERSION;
            case STORAGE_CAPACITY -> STORAGE_CAPACITY;
            case OTHER -> null;
        };
    }
}
