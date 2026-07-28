package com.c203.limit.domain.chat.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotNull UUID clientMessageId,
        @NotBlank String type,
        @Size(max = 2000) String content,
        List<Long> mediaIds) {
}
