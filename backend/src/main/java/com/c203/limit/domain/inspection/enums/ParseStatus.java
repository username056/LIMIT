package com.c203.limit.domain.inspection.enums;

/**
 * 배터리 리포트 / dxdiag 파일 파싱 결과 상태.
 * battery_report_result.parse_status, dxdiag_result.parse_status 매핑.
 */
public enum ParseStatus {
    SUCCESS,
    FAILED,
    PARTIAL
}
