package com.c203.limit.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CreateListingImageUploadUrlRequest {
    @NotBlank private final String filename;
    @NotBlank private final String contentType;
    @NotNull @Positive private final Long fileSize;
}
