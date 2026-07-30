package com.c203.limit.domain.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateProductDraftProgressRequest(
        @NotNull @Min(1) @Max(4) Integer step,
        Set<Long> confirmedChecklistItemIds) {}
