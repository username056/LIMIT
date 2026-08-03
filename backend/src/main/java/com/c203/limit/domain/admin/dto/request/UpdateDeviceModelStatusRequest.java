package com.c203.limit.domain.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDeviceModelStatusRequest(
        @NotNull Boolean isActive, @Size(max = 500) String reason, Long replacementModelId) {}
