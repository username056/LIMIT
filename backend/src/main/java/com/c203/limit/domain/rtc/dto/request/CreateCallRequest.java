package com.c203.limit.domain.rtc.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateCallRequest(
        @FutureOrPresent OffsetDateTime scheduledAt, @Size(max = 500) String memo) {}
