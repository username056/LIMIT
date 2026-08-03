package com.c203.limit.domain.inspection.agent;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionSessionRepository extends JpaRepository<InspectionSession, String> {
    Optional<InspectionSession>
            findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                    byte[] pairingCodeHash,
                    InspectionSessionStatus status,
                    LocalDateTime now);

    boolean existsByPairingCodeHashAndStatusAndExpiresAtAfter(
            byte[] pairingCodeHash, InspectionSessionStatus status, LocalDateTime now);
}
