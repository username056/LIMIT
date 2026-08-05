package com.c203.limit.domain.rtc.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record RtcSessionResponse(
        Long sessionId,
        Long callId,
        Long chatRoomId,
        Long listingId,
        Long sellerId,
        Long buyerId,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime connectedAt,
        LocalDateTime inspectionSubmittedAt,
        LocalDateTime endedAt,
        String memo,
        List<RtcChecklistItemResponse> checklistItems) {}
