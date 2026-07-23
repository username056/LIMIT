package com.c203.limit.domain.chat.dto.response;

import java.time.LocalDateTime;

import com.c203.limit.domain.chat.entity.ChatRoom;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatRoomResponse")
public record ChatRoomResponse(
        Long roomId,
        Long listingId,
        Long buyerId,
        Long sellerId,
        String status,
        LocalDateTime createdAt) {

    public static ChatRoomResponse from(ChatRoom room) {
        return new ChatRoomResponse(room.getId(), room.getListingId(), room.getBuyerId(),
                room.getSellerId(), room.getStatus().name(), room.getCreatedAt());
    }
}
