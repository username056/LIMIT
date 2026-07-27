package com.c203.limit.domain.rtc.dto.response;

public record RtcChecklistItemResponse(
        Long checklistItemId,
        String itemCode,
        String name,
        String captureGuide,
        boolean confirmed,
        String note) {}
