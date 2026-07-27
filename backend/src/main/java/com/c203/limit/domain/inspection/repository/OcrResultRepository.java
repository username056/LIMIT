package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.OcrResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {

    List<OcrResult> findAllByEvidenceIdIn(Collection<Long> evidenceIds);
}
