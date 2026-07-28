package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DxdiagResultRepository extends JpaRepository<DxdiagResult, Long> {

    List<DxdiagResult> findAllByEvidenceIdIn(Collection<Long> evidenceIds);

    Optional<DxdiagResult> findByEvidenceId(Long evidenceId);

    /** 같은 evidence를 다시 파싱하려는 걸 막기 위한 재파싱 가드용. */
    boolean existsByEvidenceId(Long evidenceId);
}
