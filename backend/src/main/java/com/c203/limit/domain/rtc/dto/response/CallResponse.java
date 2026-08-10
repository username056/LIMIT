package com.c203.limit.domain.rtc.dto.response;

import java.time.OffsetDateTime;

public record CallResponse(
        Long callId,
        Long chatRoomId,
        Long proposerId,
        Long respondentId,
        String status,
        OffsetDateTime scheduledAt,
        String memo,
        String cancelReason,
        Long rtcSessionId,
        boolean incoming,
        String counterpartName,
        OffsetDateTime sessionExpiresAt,
        OffsetDateTime inspectionSubmittedAt) {}
