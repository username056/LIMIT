package com.c203.limit.domain.inspection.reinspection.repository;

import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReinspectionRequestItemRepository extends JpaRepository<ReinspectionRequestItem, Long> {
    List<ReinspectionRequestItem> findByReinspectionRequestIdOrderByDisplayOrderAsc(Long reinspectionRequestId);
}
