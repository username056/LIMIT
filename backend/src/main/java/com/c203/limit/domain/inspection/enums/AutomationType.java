package com.c203.limit.domain.inspection.enums;

/**
 * 검수 자동화 유형. DDL: checklist_template_item.automation_type,
 * listing_checklist_item.automation_type
 */
public enum AutomationType {
    NONE,
    FILE_PARSE,
    OCR,
    AGENT_TEST
}
