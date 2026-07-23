package com.c203.limit.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "AdminAccountResponse", description = "관리자 계정")
public record AdminAccountResponse(
        Long adminId,
        String email,
        String name,
        String role,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
