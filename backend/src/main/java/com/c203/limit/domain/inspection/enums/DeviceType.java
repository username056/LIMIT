package com.c203.limit.domain.inspection.enums;

/**
 * 기기 대분류. account_removal_guide.device_type, category.device_type에 공용으로 쓰인다.
 * product 도메인의 Category가 이 enum을 직접 참조한다(중복 방지를 위해 shared-kernel 대신
 * inspection 쪽 정의를 단일 소스로 채택).
 */
public enum DeviceType {
    SMARTPHONE,
    FOLDABLE,
    TABLET,
    LAPTOP
}
