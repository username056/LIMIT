package com.c203.limit.domain.inspection.enums;

/** EXE가 선택검사를 실행해 자동으로 판정한 측정 결과. 사용자 판단(InspectionUserResult)과 분리된다. */
public enum MeasurementStatus {
    DETECTED,
    NOT_DETECTED,
    PERMISSION_DENIED,
    UNSUPPORTED,
    EXECUTION_FAILED
}
