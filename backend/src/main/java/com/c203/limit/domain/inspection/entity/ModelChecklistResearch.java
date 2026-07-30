package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "model_checklist_research")
public class ModelChecklistResearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "research_version", nullable = false)
    private int researchVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ModelChecklistResearchStatus status;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "published_template_id")
    private Long publishedTemplateId;

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ModelChecklistResearch start(Long categoryId, int researchVersion) {
        ModelChecklistResearch research = new ModelChecklistResearch();
        research.categoryId = categoryId;
        research.researchVersion = researchVersion;
        research.status = ModelChecklistResearchStatus.PROCESSING;
        research.createdAt = LocalDateTime.now();
        research.updatedAt = research.createdAt;
        return research;
    }

    public void complete(String resultJson) {
        require(ModelChecklistResearchStatus.PROCESSING);
        this.resultJson = resultJson;
        this.status = ModelChecklistResearchStatus.PENDING_REVIEW;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail() {
        require(ModelChecklistResearchStatus.PROCESSING);
        this.status = ModelChecklistResearchStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void approve(Long adminId, Long templateId, String note) {
        require(ModelChecklistResearchStatus.PENDING_REVIEW);
        this.status = ModelChecklistResearchStatus.APPROVED;
        this.reviewedByAdminId = adminId;
        this.publishedTemplateId = templateId;
        this.reviewNote = trimToNull(note);
        this.updatedAt = LocalDateTime.now();
    }

    public void reject(Long adminId, String note) {
        require(ModelChecklistResearchStatus.PENDING_REVIEW);
        this.status = ModelChecklistResearchStatus.REJECTED;
        this.reviewedByAdminId = adminId;
        this.reviewNote = trimToNull(note);
        this.updatedAt = LocalDateTime.now();
    }

    private void require(ModelChecklistResearchStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("invalid checklist research state");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
