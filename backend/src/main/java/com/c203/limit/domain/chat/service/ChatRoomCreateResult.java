package com.c203.limit.domain.chat.service;

import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;

public record ChatRoomCreateResult(boolean created, ChatRoomResponse response) {
    public static ChatRoomCreateResult created(ChatRoomResponse response) {
        return new ChatRoomCreateResult(true, response);
    }

    public static ChatRoomCreateResult existing(ChatRoomResponse response) {
        return new ChatRoomCreateResult(false, response);
    }
}
