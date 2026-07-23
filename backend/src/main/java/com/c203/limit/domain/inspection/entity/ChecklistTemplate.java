package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리(리프 모델)별 검수 체크리스트 템플릿. DDL: checklist_template. PUBLISHED 이후 항목 수정 금지.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "checklist_template")
public class ChecklistTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChecklistTemplateStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChecklistTemplate createDraft(Long categoryId, int version) {
        ChecklistTemplate template = new ChecklistTemplate();
        template.categoryId = categoryId;
        template.version = version;
        template.status = ChecklistTemplateStatus.DRAFT;
        template.createdAt = LocalDateTime.now();
        return template;
    }

    public void publish() {
        this.status = ChecklistTemplateStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void deprecate() {
        this.status = ChecklistTemplateStatus.DEPRECATED;
    }
}
