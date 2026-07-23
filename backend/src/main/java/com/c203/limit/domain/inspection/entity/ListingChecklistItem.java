package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매물 등록 시점에 템플릿 항목을 스냅샷으로 고정한 체크리스트 항목. DDL: listing_checklist_item
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "listing_checklist_item")
public class ListingChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_item_id")
    private ChecklistTemplateItem templateItem;

    @Column(name = "item_code", nullable = false, length = 30)
    private String itemCode;

    @Column(nullable = false, length = 100)
    private String name;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status", nullable = false, length = 30)
    private ChecklistItemCompletionStatus completionStatus;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static ListingChecklistItem createFromTemplateItem(Long listingId, ChecklistTemplateItem templateItem) {
        ListingChecklistItem item = new ListingChecklistItem();
        item.listingId = listingId;
        item.templateItem = templateItem;
        item.itemCode = templateItem.getItemCode();
        item.name = templateItem.getName();
        item.captureGuide = templateItem.getCaptureGuide();
        item.evidenceType = templateItem.getEvidenceType();
        item.automationType = templateItem.getAutomationType();
        item.parserType = templateItem.getParserType();
        item.isRequired = templateItem.isRequired();
        item.allowedFormats = templateItem.getAllowedFormats();
        item.minCount = templateItem.getMinCount();
        item.maxCount = templateItem.getMaxCount();
        item.minDurationSec = templateItem.getMinDurationSec();
        item.maxDurationSec = templateItem.getMaxDurationSec();
        item.maxFileSizeMb = templateItem.getMaxFileSizeMb();
        item.visibleToBuyer = templateItem.isVisibleToBuyer();
        item.privacyMaskingRequired = templateItem.isPrivacyMaskingRequired();
        item.completionStatus = ChecklistItemCompletionStatus.PENDING;
        item.displayOrder = templateItem.getDisplayOrder();
        return item;
    }

    public void markSubmitted() {
        this.completionStatus = ChecklistItemCompletionStatus.SUBMITTED;
    }

    public void markCompleted() {
        this.completionStatus = ChecklistItemCompletionStatus.COMPLETED;
    }
}
