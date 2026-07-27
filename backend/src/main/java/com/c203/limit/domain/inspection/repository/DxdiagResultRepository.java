package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DxdiagResultRepository extends JpaRepository<DxdiagResult, Long> {

    List<DxdiagResult> findAllByEvidenceIdIn(Collection<Long> evidenceIds);
}
