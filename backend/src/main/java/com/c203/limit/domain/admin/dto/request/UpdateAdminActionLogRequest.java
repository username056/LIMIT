package com.c203.limit.domain.admin.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateAdminActionLogRequest(@Size(max = 500) String reason) {}
