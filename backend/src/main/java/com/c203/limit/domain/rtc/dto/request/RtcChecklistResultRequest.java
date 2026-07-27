package com.c203.limit.domain.rtc.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RtcChecklistResultRequest(
        @NotNull Long checklistItemId, @NotNull Boolean confirmed, @Size(max = 500) String note) {}
