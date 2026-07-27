package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatteryReportResultRepository extends JpaRepository<BatteryReportResult, Long> {

    List<BatteryReportResult> findAllByEvidenceIdIn(Collection<Long> evidenceIds);
}
