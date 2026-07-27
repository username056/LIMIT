package com.c203.limit.domain.inspection.repository;

public interface ListingChecklistCountProjection {
    Long getListingId();

    Long getRequiredCount();

    Long getCompletedRequiredCount();
}
