package com.c203.limit.domain.product.repository;

public interface ListingThumbnailProjection {
    Long getListingId();

    String getCdnUrl();

    String getS3Key();
}
