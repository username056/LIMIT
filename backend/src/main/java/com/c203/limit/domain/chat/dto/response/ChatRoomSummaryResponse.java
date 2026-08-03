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
        // 목록에 한 줄로 보여 줄 마지막 메시지. 예전에는 마지막 메시지의 번호·순서·시각만
        // 있어서, 화면이 두 번째 줄에 상품명을 대신 넣고 있었습니다.
        // 사진·영상은 파일명 대신 "사진을 보냈습니다."로, 긴 글은 100자로 잘라 보냅니다.
        @Schema(description = "마지막 메시지 미리보기. 주고받은 말이 없으면 null", example = "오늘 오후에 가능하실까요?")
        String lastMessagePreview,
        String status,
        Long lastMessageId,
        long lastMessageSequence,
        LocalDateTime lastMessageAt,
        long unreadCount,
        long counterpartLastReadSequence,
        LocalDateTime createdAt) {
}
