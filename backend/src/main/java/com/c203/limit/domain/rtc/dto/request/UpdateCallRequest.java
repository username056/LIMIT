package com.c203.limit.domain.rtc.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record UpdateCallRequest(
        @NotNull @FutureOrPresent OffsetDateTime scheduledAt, @Size(max = 500) String memo) {}
