package com.c203.limit.domain.chat.repository;

import java.time.LocalDateTime;

import com.c203.limit.domain.chat.domain.ChatRoomStatus;

public interface ChatRoomSummaryProjection {
    Long getRoomId();
    Long getListingId();
    Long getBuyerId();
    Long getSellerId();
    ChatRoomStatus getStatus();
    Long getLastMessageId();
    long getLastMessageSeq();
    LocalDateTime getLastMessageAt();
    long getLastReadSeq();
    long getCounterpartLastReadSeq();
    LocalDateTime getCreatedAt();
}
