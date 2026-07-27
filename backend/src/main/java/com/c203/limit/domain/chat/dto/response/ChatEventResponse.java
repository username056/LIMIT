package com.c203.limit.domain.chat.dto.response;

public record ChatEventResponse(
        String type,
        Long roomId,
        ChatMessageResponse message,
        Long readerId,
        Long lastReadSeq) {

    public static ChatEventResponse message(Long roomId, ChatMessageResponse message) {
        return new ChatEventResponse("MESSAGE", roomId, message, null, null);
    }

    public static ChatEventResponse read(Long roomId, Long readerId, Long lastReadSeq) {
        return new ChatEventResponse("READ", roomId, null, readerId, lastReadSeq);
    }
}
