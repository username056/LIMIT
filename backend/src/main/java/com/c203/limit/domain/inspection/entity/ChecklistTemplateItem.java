package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검수 템플릿에 속한 개별 체크리스트 항목 정의. DDL: checklist_template_item
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "checklist_template_item")
public class ChecklistTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_template_id")
    private ChecklistTemplate checklistTemplate;

    @Column(name = "item_code", nullable = false, length = 30)
    private String itemCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String purpose;

    @Column(name = "capture_guide", nullable = false, columnDefinition = "TEXT")
    private String captureGuide;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_type", nullable = false, length = 30)
    private AutomationType automationType;

    @Column(name = "parser_type", length = 50)
    private String parserType;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Column(name = "allowed_formats", length = 100)
    private String allowedFormats;

    @Column(name = "min_count")
    private Integer minCount;

    @Column(name = "max_count")
    private Integer maxCount;

    @Column(name = "min_duration_sec")
    private Integer minDurationSec;

    @Column(name = "max_duration_sec")
    private Integer maxDurationSec;

    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb;

    @Column(name = "visible_to_buyer", nullable = false)
    private boolean visibleToBuyer;

    @Column(name = "privacy_masking_required", nullable = false)
    private boolean privacyMaskingRequired;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static ChecklistTemplateItem create(
            ChecklistTemplate checklistTemplate,
            String itemCode,
            String name,
            String purpose,
            String captureGuide,
            EvidenceType evidenceType,
            AutomationType automationType,
            boolean isRequired,
            int displayOrder) {
        ChecklistTemplateItem item = new ChecklistTemplateItem();
        item.checklistTemplate = checklistTemplate;
        item.itemCode = itemCode;
        item.name = name;
        item.purpose = purpose;
        item.captureGuide = captureGuide;
        item.evidenceType = evidenceType;
        item.automationType = automationType == null ? AutomationType.NONE : automationType;
        item.isRequired = isRequired;
        item.visibleToBuyer = true;
        item.privacyMaskingRequired = false;
        item.displayOrder = displayOrder;
        return item;
    }

    public static ChecklistTemplateItem createGenerated(
            ChecklistTemplate checklistTemplate,
            String itemCode,
            String name,
            String purpose,
            String captureGuide,
            EvidenceType evidenceType,
            AutomationType automationType,
            String parserType,
            boolean isRequired,
            int displayOrder) {
        ChecklistTemplateItem item = create(
                checklistTemplate,
                itemCode,
                name,
                purpose,
                captureGuide,
                evidenceType,
                automationType,
                isRequired,
                displayOrder);
        item.parserType = parserType;
        if (evidenceType == EvidenceType.PHOTO) {
            item.allowedFormats = "jpg,jpeg,png";
            item.minCount = 1;
            // 설정 정보 화면(OCR)은 화면 한 장만 있으면 되므로 여러 장 올릴 필요가 없다.
            item.maxCount = automationType == AutomationType.OCR ? 1 : 5;
            item.maxFileSizeMb = 20;
        } else if (evidenceType == EvidenceType.VIDEO) {
            item.allowedFormats = "mp4,mov";
            item.minCount = 1;
            item.maxCount = 1;
            item.minDurationSec = 5;
            item.maxDurationSec = 60;
            item.maxFileSizeMb = 100;
        } else if (evidenceType == EvidenceType.DIAGNOSTIC_FILE) {
            item.allowedFormats =
                    "BATTERY_REPORT".equals(parserType) ? "html" : "txt";
            item.minCount = 1;
            item.maxCount = 1;
            item.maxFileSizeMb = 10;
        }
        return item;
    }
}
