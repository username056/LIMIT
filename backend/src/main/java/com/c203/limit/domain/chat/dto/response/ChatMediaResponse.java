package com.c203.limit.domain.chat.dto.response;

import com.c203.limit.domain.chat.entity.ChatMedia;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatMediaResponse", description = "채팅 이미지·영상 파일")
public record ChatMediaResponse(
        Long mediaId,
        String type,
        String contentUrl,
        String originalFilename,
        String mimeType,
        long fileSizeBytes) {

    public static ChatMediaResponse from(ChatMedia media) {
        return new ChatMediaResponse(
                media.getId(),
                media.getType().name(),
                "/api/v1/chat-media/" + media.getId() + "/content",
                media.getOriginalFilename(),
                media.getMimeType(),
                media.getFileSizeBytes());
    }
}
