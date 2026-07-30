package com.c203.limit.domain.product.dto.request;

import com.c203.limit.domain.product.entity.ListingImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CompleteListingImageRequest {
    @NotBlank private final String uploadId;
    @NotNull private final ListingImageType imageType;
    @NotNull @PositiveOrZero private final Integer displayOrder;
}
