package com.c203.limit.domain.product.moderation.repository;

import com.c203.limit.domain.product.moderation.entity.ListingRestorationRequest;
import com.c203.limit.domain.product.moderation.entity.RestorationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ListingRestorationRequestRepository extends
        JpaRepository<ListingRestorationRequest, Long>,
        JpaSpecificationExecutor<ListingRestorationRequest> {
    boolean existsByListingIdAndStatus(Long listingId, RestorationRequestStatus status);

    long countByStatus(RestorationRequestStatus status);
}
