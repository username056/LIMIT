package com.c203.limit.domain.product.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
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

    @Column(length = 50)
    private String color;

    @Column(name = "storage_gb")
    private Integer storageGb;

    @Column(name = "trade_region", length = 100)
    private String tradeRegion;

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

    // 예약 만료 판정 기준 시각. 결제 유예 적용 시 이 값만 연장된다.
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // 판매자가 상품 전달 완료를 기록한 시각.
    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    // handedOverAt 기준으로 계산되는 자동 구매확정 배치 기준 시각.
    @Column(name = "auto_confirm_at")
    private LocalDateTime autoConfirmAt;

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

    public static Listing createDraft(
            Long sellerId,
            Category category,
            String title,
            String description,
            int price,
            String color,
            Integer storageGb,
            String tradeRegion,
            Long checklistTemplateId) {
        Listing listing = createDraft(
                sellerId, category, title, description, price, checklistTemplateId);
        listing.color = color;
        listing.storageGb = storageGb;
        listing.tradeRegion = tradeRegion;
        return listing;
    }

    public void updateDraft(String title, String description, int price) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        this.price = price;
    }

    public void updateDraft(
            String title,
            String description,
            boolean descriptionSpecified,
            Integer price,
            String color,
            boolean colorSpecified,
            Integer storageGb,
            boolean storageGbSpecified,
            String tradeRegion) {
        requireStatus(ListingStatus.DRAFT, ErrorCode.PRODUCT_EDIT_NOT_ALLOWED);
        if (title != null) this.title = title;
        if (descriptionSpecified) this.description = description;
        if (price != null) this.price = price;
        if (colorSpecified) this.color = color;
        if (storageGbSpecified) this.storageGb = storageGb;
        if (tradeRegion != null) this.tradeRegion = tradeRegion;
    }

    public void publish() {
        requireStatus(ListingStatus.DRAFT, ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        if (!precheckCompleted) {
            throw new BusinessException(ErrorCode.REQUIRED_EVIDENCE_INCOMPLETE);
        }
        this.status = ListingStatus.ON_SALE;
    }

    public void completePrecheck() {
        this.precheckCompleted = true;
    }

    public void hide() {
        if (status != ListingStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        }
        this.status = ListingStatus.HIDDEN;
    }

    /** ON_SALE 매물을 구매자에게 예약 처리한다. */
    public void reserve(Long buyerId) {
        requireStatus(ListingStatus.ON_SALE, ErrorCode.LISTING_NOT_ON_SALE);
        this.buyerId = buyerId;
        this.status = ListingStatus.RESERVED;
        this.reservedAt = LocalDateTime.now();
    }

    /** 구매자/판매자 요청으로 예약을 취소하고 다시 판매중 상태로 되돌린다. */
    public void cancelReservation() {
        requireStatus(ListingStatus.RESERVED, ErrorCode.LISTING_NOT_RESERVED);
        releaseReservation();
    }

    /** 결제 기한 만료 등 시스템 처리로 예약을 롤백하고 다시 판매중 상태로 되돌린다. */
    public void expireReservation() {
        requireStatus(ListingStatus.RESERVED, ErrorCode.LISTING_NOT_RESERVED);
        releaseReservation();
    }

    private void releaseReservation() {
        this.buyerId = null;
        this.reservedAt = null;
        this.status = ListingStatus.ON_SALE;
    }

    public void markPaid() {
        requireStatus(ListingStatus.RESERVED, ErrorCode.LISTING_NOT_RESERVED);
        this.status = ListingStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /** 결제 완료된 매물을 검수 단계로 전환한다. */
    public void markInspecting() {
        requireStatus(ListingStatus.PAID, ErrorCode.LISTING_NOT_PAID);
        this.status = ListingStatus.INSPECTING;
    }

    public void confirm() {
        requireStatus(ListingStatus.INSPECTING, ErrorCode.LISTING_NOT_INSPECTING);
        this.status = ListingStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void settle() {
        requireStatus(ListingStatus.CONFIRMED, ErrorCode.LISTING_NOT_CONFIRMED);
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

    /**
     * 현재 상태가 기대 상태와 다르면 거부한다. 각 전이 메서드의 사전조건 역할을 하며, 동일 전이를 중복
     * 호출하거나 순서를 건너뛴 호출을 함께 막는다.
     */
    private void requireStatus(ListingStatus expected, ErrorCode errorCode) {
        if (this.status != expected) {
            throw new BusinessException(errorCode);
        }
    }
}
