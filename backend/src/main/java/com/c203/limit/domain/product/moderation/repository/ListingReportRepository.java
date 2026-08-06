package com.c203.limit.domain.product.moderation.repository;

import com.c203.limit.domain.product.moderation.entity.ListingReport;
import com.c203.limit.domain.product.moderation.entity.ListingReportStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ListingReportRepository
        extends JpaRepository<ListingReport, Long>, JpaSpecificationExecutor<ListingReport> {
    boolean existsByListingIdAndReporterId(Long listingId, Long reporterId);

    List<ListingReport> findByListingIdAndStatusOrderByCreatedAtAsc(
            Long listingId, ListingReportStatus status);

    List<ListingReport> findByListingIdAndStatusInOrderByReviewedAtDesc(
            Long listingId, List<ListingReportStatus> statuses);

    long countByStatus(ListingReportStatus status);

    long countByListingIdAndStatus(Long listingId, ListingReportStatus status);
}
