package com.c203.limit.domain.chat.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChatReadRequest(@NotNull @Min(0) Long lastReadSeq) {
}
