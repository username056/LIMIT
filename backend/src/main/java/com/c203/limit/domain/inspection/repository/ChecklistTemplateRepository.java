package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, Long> {
    Optional<ChecklistTemplate> findFirstByCategoryIdAndStatusOrderByVersionDesc(
            Long categoryId, ChecklistTemplateStatus status);
}
