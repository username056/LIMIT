package com.c203.limit.domain.inspection.agent;

import com.c203.limit.domain.inspection.enums.TestType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionSessionTestResultRepository
        extends JpaRepository<InspectionSessionTestResult, Long> {
    Optional<InspectionSessionTestResult> findBySessionKeyAndClientResultId(
            String sessionKey, UUID clientResultId);

    Optional<InspectionSessionTestResult> findTopBySessionKeyAndTestTypeOrderByAttemptNoDesc(
            String sessionKey, TestType testType);

    List<InspectionSessionTestResult> findAllBySessionKeyOrderByCreatedAtAscIdAsc(String sessionKey);

    boolean existsByChecklistItemId(Long checklistItemId);
}
