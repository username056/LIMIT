package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.ListingStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingStatusHistoryRepository extends JpaRepository<ListingStatusHistory, Long> {

    List<ListingStatusHistory> findByListingIdOrderByCreatedAtAsc(Long listingId);
}
