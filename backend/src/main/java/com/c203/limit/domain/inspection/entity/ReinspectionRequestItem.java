package com.c203.limit.domain.inspection.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재검수 요청 안에서 구매자가 지목한 개별 체크리스트 항목. DDL: reinspection_request_item */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reinspection_request_item")
public class ReinspectionRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reinspection_request_id")
    private ReinspectionRequest reinspectionRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_checklist_item_id")
    private ListingChecklistItem listingChecklistItem;

    @Column(name = "item_name_snapshot", nullable = false, length = 100)
    private String itemNameSnapshot;

    @Column(name = "request_content", nullable = false, length = 1000)
    private String requestContent;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ReinspectionRequestItem create(
            ReinspectionRequest reinspectionRequest,
            ListingChecklistItem listingChecklistItem,
            String requestContent,
            int displayOrder) {
        ReinspectionRequestItem item = new ReinspectionRequestItem();
        item.reinspectionRequest = reinspectionRequest;
        item.listingChecklistItem = listingChecklistItem;
        item.itemNameSnapshot = listingChecklistItem.getName();
        item.requestContent = requestContent;
        item.displayOrder = displayOrder;
        item.createdAt = LocalDateTime.now();
        return item;
    }
}
