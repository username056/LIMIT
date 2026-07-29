package com.c203.limit.domain.inspection.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReinspectionRequestCreateRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotEmpty @Size(max = 100) List<@Valid Item> items) {

    public record Item(
            @NotNull Long checklistItemId,
            @NotBlank @Size(max = 1000) String requestContent) {}
}
