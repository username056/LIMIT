package com.c203.limit.domain.inspection.enums;

/** 검수 템플릿 상태. DDL: checklist_template.status. PUBLISHED 이후 항목 수정 금지. */
public enum ChecklistTemplateStatus {
    DRAFT,
    PUBLISHED,
    DEPRECATED
}
