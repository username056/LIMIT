package com.c203.limit.domain.rtc.dto.response;

import java.time.LocalDateTime;

public record CallResponse(
        Long callId,
        Long chatRoomId,
        Long proposerId,
        Long respondentId,
        String status,
        LocalDateTime scheduledAt,
        String memo,
        String cancelReason,
        Long rtcSessionId,
        boolean incoming) {}
