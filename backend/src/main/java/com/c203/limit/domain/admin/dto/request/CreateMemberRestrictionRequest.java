package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

@Schema(name = "CreateMemberRestrictionRequest", description = "회원 제재 등록 요청")
public record CreateMemberRestrictionRequest(
        @NotBlank @Schema(example = "PURCHASE") String restrictionType,
        @NotBlank @Schema(example = "MACRO_USE") String reasonCode,
        @NotBlank @Schema(example = "비정상 반복 요청 탐지") String reasonDetail,
        @NotNull @Schema(example = "2026-07-22T14:00:00+09:00") OffsetDateTime startsAt,
        @NotNull @Schema(example = "2026-07-29T14:00:00+09:00") OffsetDateTime endsAt) {}
