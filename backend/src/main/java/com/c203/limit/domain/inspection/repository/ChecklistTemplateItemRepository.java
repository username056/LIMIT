package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateItemRepository extends JpaRepository<ChecklistTemplateItem, Long> {
    List<ChecklistTemplateItem> findByChecklistTemplateIdOrderByDisplayOrderAsc(Long templateId);
}
