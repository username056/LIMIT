package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.Evidence;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    Optional<Evidence> findByIdAndListingId(Long id, Long listingId);

    List<Evidence> findAllByListingChecklistItem_Id(Long listingChecklistItemId);

    @EntityGraph(attributePaths = "listingChecklistItem")
    List<Evidence> findAllByListingId(Long listingId);

    List<Evidence> findAllByListingChecklistItem_IdOrderByUploadedAtAscIdAsc(
            Long listingChecklistItemId);

    boolean existsByListingChecklistItem_IdAndUploadedAtAfter(
            Long listingChecklistItemId, LocalDateTime uploadedAt);

    long countByListingChecklistItem_Id(Long listingChecklistItemId);
}
