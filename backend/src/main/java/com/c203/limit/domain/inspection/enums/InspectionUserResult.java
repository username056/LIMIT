package com.c203.limit.domain.inspection.enums;

/** 선택검사 결과에 대한 사용자(판매자) 확인. 사용자 확인이 필요 없는 검사는 null을 허용한다. */
public enum InspectionUserResult {
    USER_CONFIRMED,
    USER_REPORTED_ISSUE,
    SKIPPED
}
