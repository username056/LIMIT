package com.c203.limit.domain.rtc.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateCallRequest(
        @FutureOrPresent LocalDateTime scheduledAt, @Size(max = 500) String memo) {}
