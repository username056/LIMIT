package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.enums.ReinspectionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReinspectionRequestRepository extends JpaRepository<ReinspectionRequest, Long> {
    Optional<ReinspectionRequest> findByRequestKey(String requestKey);

    long countByChatRoomIdAndStatus(Long chatRoomId, ReinspectionStatus status);
}
