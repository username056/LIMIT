package com.c203.limit.domain.product.dto.request;

import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdateProductDraftProgressRequest(
        @NotNull @Min(1) @Max(4) Integer step,
        @NotNull @Valid List<ChecklistItemResult> results) {

    public record ChecklistItemResult(
            @NotNull @Positive Long checklistItemId, @NotNull DeviceCheckResult result) {}
}
