package com.c203.limit.domain.inspection.agent;

import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionSessionRepository extends JpaRepository<InspectionSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from InspectionSession session where session.sessionKey = :sessionKey")
    Optional<InspectionSession> findBySessionKeyForUpdate(@Param("sessionKey") String sessionKey);

    Optional<InspectionSession>
            findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                    byte[] pairingCodeHash,
                    InspectionSessionStatus status,
                    LocalDateTime now);

    boolean existsByPairingCodeHashAndStatusAndExpiresAtAfter(
            byte[] pairingCodeHash, InspectionSessionStatus status, LocalDateTime now);
}
