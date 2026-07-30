package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.product.entity.ListingImage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ListingImageResponse {
    private final Long imageId;
    private final String imageType;
    private final Integer displayOrder;
    private final String imageUrl;
    private final String mimeType;

    public static ListingImageResponse of(ListingImage image, String imageUrl) {
        return new ListingImageResponse(
                image.getId(),
                image.getImageType().name(),
                image.getDisplayOrder(),
                imageUrl,
                image.getMimeType());
    }
}
