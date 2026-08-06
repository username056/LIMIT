package com.c203.limit.domain.product.moderation.repository;

import com.c203.limit.domain.product.moderation.entity.ModerationRiskSignal;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ModerationRiskSignalRepository extends
        JpaRepository<ModerationRiskSignal, Long>,
        JpaSpecificationExecutor<ModerationRiskSignal> {
    Optional<ModerationRiskSignal> findByFingerprint(String fingerprint);

    Page<ModerationRiskSignal> findByStatusOrderByCreatedAtDesc(
            ModerationRiskStatus status, Pageable pageable);

    long countByStatus(ModerationRiskStatus status);

    long countByListingIdAndStatus(Long listingId, ModerationRiskStatus status);

    Optional<ModerationRiskSignal> findFirstBySellerIdAndTypeAndStatus(
            Long sellerId, ModerationRiskType type, ModerationRiskStatus status);
}
