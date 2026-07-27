package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingChecklistItemRepository extends JpaRepository<ListingChecklistItem, Long> {}
