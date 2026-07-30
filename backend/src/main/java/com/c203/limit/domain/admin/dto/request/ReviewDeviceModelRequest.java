package com.c203.limit.domain.admin.dto.request;

import jakarta.validation.constraints.Size;

public record ReviewDeviceModelRequest(@Size(max = 500) String note) {}
