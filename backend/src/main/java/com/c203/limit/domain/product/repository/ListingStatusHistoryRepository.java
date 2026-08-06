package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.ListingStatusHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingStatusHistoryRepository extends JpaRepository<ListingStatusHistory, Long> {

    List<ListingStatusHistory> findByListingIdOrderByCreatedAtAsc(Long listingId);

    @Query(
            """
            SELECT history.actorId AS sellerId,
                   COUNT(DISTINCT history.listing.id) AS publishingCount
              FROM ListingStatusHistory history
             WHERE history.fromStatus = com.c203.limit.domain.product.entity.ListingStatus.DRAFT
               AND history.toStatus = com.c203.limit.domain.product.entity.ListingStatus.ON_SALE
               AND history.createdAt >= :since
               AND history.actorId IS NOT NULL
             GROUP BY history.actorId
            """)
    List<SellerPublishingCountProjection> countFirstPublicationsBySellerSince(
            @Param("since") LocalDateTime since);

    @Query(
            """
            SELECT COUNT(DISTINCT history.listing.id)
              FROM ListingStatusHistory history
             WHERE history.fromStatus = com.c203.limit.domain.product.entity.ListingStatus.DRAFT
               AND history.toStatus = com.c203.limit.domain.product.entity.ListingStatus.ON_SALE
               AND history.createdAt >= :since
               AND history.actorId = :sellerId
            """)
    long countFirstPublicationsBySellerSince(
            @Param("sellerId") Long sellerId, @Param("since") LocalDateTime since);
}
