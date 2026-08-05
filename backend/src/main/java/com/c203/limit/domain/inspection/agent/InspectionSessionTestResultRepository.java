package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.enums.TestType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionSessionTestResultRepository
        extends JpaRepository<InspectionSessionTestResult, Long> {
    Optional<InspectionSessionTestResult> findBySessionKeyAndClientResultId(
            String sessionKey, UUID clientResultId);

    Optional<InspectionSessionTestResult> findTopBySessionKeyAndTestTypeOrderByAttemptNoDesc(
            String sessionKey, TestType testType);

    List<InspectionSessionTestResult> findAllBySessionKeyOrderByCreatedAtAscIdAsc(String sessionKey);

    @Query("""
            select result
            from InspectionSessionTestResult result
            join InspectionSession session on session.sessionKey = result.sessionKey
            where session.listingId = :listingId
            order by result.createdAt desc, result.id desc
            """)
    List<InspectionSessionTestResult> findAllByListingIdNewestFirst(
            @Param("listingId") Long listingId);

    boolean existsByChecklistItemId(Long checklistItemId);
}
