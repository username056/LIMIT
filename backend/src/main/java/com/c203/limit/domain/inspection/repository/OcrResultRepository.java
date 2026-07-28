package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {

    List<OcrResult> findAllByEvidenceIdIn(Collection<Long> evidenceIds);

    Optional<OcrResult> findByEvidenceIdAndFieldType(Long evidenceId, OcrFieldType fieldType);

    /** 같은 evidence를 다시 OCR 처리하려는 걸 막기 위한 재파싱 가드용. */
    boolean existsByEvidenceId(Long evidenceId);
}
