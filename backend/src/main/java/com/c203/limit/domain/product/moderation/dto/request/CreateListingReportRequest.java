package com.c203.limit.domain.product.moderation.dto.request;

import com.c203.limit.domain.product.moderation.entity.ListingReportCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateListingReportRequest", description = "상품 신고 등록 요청")
public record CreateListingReportRequest(
        @NotNull ListingReportCategory category,
        @NotBlank @Size(min = 5, max = 1000) String detail) {}
