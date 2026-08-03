package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.ListingStatus;

public interface ListingStatusCountProjection {
    ListingStatus getStatus();

    long getListingCount();
}
