package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingChecklistItemRepository
        extends JpaRepository<ListingChecklistItem, Long> {
    List<ListingChecklistItem> findByListingIdOrderByDisplayOrderAsc(Long listingId);

    List<ListingChecklistItem> findByListingIdAndIsRequiredTrueOrderByDisplayOrderAsc(Long listingId);

    Optional<ListingChecklistItem> findByIdAndListingId(Long id, Long listingId);

    List<ListingChecklistItem> findAllByIdInAndListingId(List<Long> ids, Long listingId);

    long countByListingIdAndIsRequiredTrue(Long listingId);

    long countByListingIdAndIsRequiredTrueAndCompletionStatus(
            Long listingId, ChecklistItemCompletionStatus status);

    @Query(
            """
            SELECT item.listingId AS listingId,
                   SUM(CASE WHEN item.isRequired = true THEN 1 ELSE 0 END) AS requiredCount,
                   SUM(CASE WHEN item.isRequired = true AND item.completionStatus = :completedStatus
                            THEN 1 ELSE 0 END) AS completedRequiredCount
              FROM ListingChecklistItem item
             WHERE item.listingId IN :listingIds
             GROUP BY item.listingId
            """)
    List<ListingChecklistCountProjection> countRequiredByListingIds(
            @Param("listingIds") List<Long> listingIds,
            @Param("completedStatus") ChecklistItemCompletionStatus completedStatus);

    List<ListingChecklistItem> findAllByListingIdOrderByDisplayOrderAsc(Long listingId);
}
