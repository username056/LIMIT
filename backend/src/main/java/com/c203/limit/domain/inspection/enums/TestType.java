package com.c203.limit.domain.inspection.enums;

/**
 * EXE 선택검사(에이전트 기반) 종류. listing_checklist_item.item_code와 1:1 매핑할 예정이며
 * automation_type이 AGENT_TEST인 항목에서 사용할 계획이다. 매핑·시딩은 아직 구현되지 않았다.
 */
public enum TestType {
    CAMERA,
    MICROPHONE,
    KEYBOARD,
    TOUCHPAD,
    SPEAKER,
    DISPLAY,
    CHARGING,
    NUMPAD
}
