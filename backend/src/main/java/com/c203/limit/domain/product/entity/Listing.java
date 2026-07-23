package com.c203.limit.domain.product.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중고 전자기기 매물(상품). DDL: listing. seller_id/buyer_id는 회원 도메인 참조라 FK 없이 ID만 보관한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "listing")
public class Listing extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(name = "checklist_template_id", nullable = false)
    private Long checklistTemplateId;

    @Column(name = "precheck_completed", nullable = false)
    private boolean precheckCompleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListingStatus status;

    @Column(name = "suspended_reason", length = 200)
    private String suspendedReason;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Listing createDraft(
            Long sellerId,
            Category category,
            String title,
            String description,
            int price,
            Long checklistTemplateId) {
        Listing listing = new Listing();
        listing.sellerId = sellerId;
        listing.category = category;
        listing.title = title;
        listing.description = description;
        listing.price = price;
        listing.checklistTemplateId = checklistTemplateId;
        listing.precheckCompleted = false;
        listing.status = ListingStatus.DRAFT;
        return listing;
    }

    public void updateDraft(String title, String description, int price) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        this.price = price;
    }

    public void reserve(Long buyerId) {
        this.buyerId = buyerId;
        this.status = ListingStatus.RESERVED;
        this.reservedAt = LocalDateTime.now();
    }

    public void markPaid() {
        this.status = ListingStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void confirm() {
        this.status = ListingStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void settle() {
        this.status = ListingStatus.SETTLED;
        this.settledAt = LocalDateTime.now();
    }

    public void suspend(String reason) {
        this.status = ListingStatus.SUSPENDED;
        this.suspendedReason = reason;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
