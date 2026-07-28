package com.c203.limit.domain.chat.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatRoomSummaryResponse")
public record ChatRoomSummaryResponse(
        Long roomId,
        Long listingId,
        Long counterpartId,
        String counterpartNickname,
        String listingTitle,
        String listingThumbnailUrl,
        String status,
        Long lastMessageId,
        long lastMessageSequence,
        LocalDateTime lastMessageAt,
        long unreadCount,
        LocalDateTime createdAt) {
}
