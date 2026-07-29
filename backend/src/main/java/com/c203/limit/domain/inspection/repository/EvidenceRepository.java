package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.Evidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findAllByListingChecklistItem_Id(Long listingChecklistItemId);

    List<Evidence> findAllByListingId(Long listingId);
}
