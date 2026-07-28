package com.c203.limit.domain.inspection.enums;

/** 구매자 요약 화면에서 필드 하나의 상태. 별도 확정 테이블이 없어 "확정 여부"는 구분하지 않고, 값이 있는지만 구분한다. */
public enum DiagnosisSummaryStatus {
    AVAILABLE,
    EXTRACTION_FAILED
}
