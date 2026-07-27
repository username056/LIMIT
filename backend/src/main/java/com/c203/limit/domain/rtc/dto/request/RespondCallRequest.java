package com.c203.limit.domain.rtc.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RespondCallRequest(@NotNull Boolean accepted, @Size(max = 500) String reason) {}
