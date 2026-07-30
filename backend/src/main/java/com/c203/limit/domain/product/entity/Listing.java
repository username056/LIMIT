package com.c203.limit.domain.product.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;
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

    /**
     * 판매자가 상품 정보(제목·설명·가격·옵션)를 고칠 수 있는 상태.
     * 초안뿐 아니라 이미 등록을 끝낸 판매 중·숨김 매물도 허용한다 — 가격 오타처럼 등록 후에야
     * 발견하는 실수를 되돌릴 방법이 필요하기 때문이다. 반면 RESERVED 이후는 구매자가 그 조건을
     * 보고 결제·검수에 들어간 뒤라 수정을 막는다.
     */
    private static final Set<ListingStatus> EDITABLE_STATUSES =
            Set.of(ListingStatus.DRAFT, ListingStatus.ON_SALE, ListingStatus.HIDDEN);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * 카탈로그에 없는 기기를 '기타 (직접 입력)' 모델로 등록할 때 판매자가 적은 실제 제조사·모델명.
     * 그 모델 행 하나에 여러 기기가 매달리므로 매물마다 따로 보관하고, 값이 있으면 상세·목록에서
     * 카탈로그 모델명 대신 이 값을 노출한다.
     */
    @Column(name = "custom_manufacturer", length = 50)
    private String customManufacturer;

    @Column(name = "custom_model_name", length = 100)
    private String customModelName;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private long price;

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

    @Column(name = "draft_step", nullable = false)
    private int draftStep;

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
            long price,
            Long checklistTemplateId) {
        Listing listing = new Listing();
        listing.sellerId = sellerId;
        listing.category = category;
        listing.title = title;
        listing.description = description;
        listing.price = price;
        listing.checklistTemplateId = checklistTemplateId;
        listing.precheckCompleted = false;
        listing.draftStep = 1;
        listing.status = ListingStatus.DRAFT;
        return listing;
    }

    public static Listing createDraft(
            Long sellerId,
            Category category,
            String title,
            String description,
            long price,
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

    /** 카탈로그에 없는 기기를 판매자가 직접 입력한 제조사·모델명과 함께 등록한다. */
    public void applyCustomModel(String customManufacturer, String customModelName) {
        this.customManufacturer = trimToNull(customManufacturer);
        this.customModelName = trimToNull(customModelName);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public void updateDraft(String title, String description, long price) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        this.price = price;
    }

    /** 판매자가 상품 정보를 고친다. 초안·판매 중·숨김 상태에서만 허용한다. */
    public void updateBySeller(
            String title,
            String description,
            boolean descriptionSpecified,
            Long price,
            String color,
            boolean colorSpecified,
            Integer storageGb,
            boolean storageGbSpecified,
            String tradeRegion) {
        if (!EDITABLE_STATUSES.contains(this.status)) {
            throw new BusinessException(ErrorCode.PRODUCT_EDIT_NOT_ALLOWED);
        }
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

    public void updateDraftStep(int draftStep) {
        requireStatus(ListingStatus.DRAFT, ErrorCode.PRODUCT_EDIT_NOT_ALLOWED);
        if (draftStep < 1 || draftStep > 4) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        this.draftStep = draftStep;
    }

    public void hide() {
        if (status != ListingStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        }
        this.status = ListingStatus.HIDDEN;
    }

    /**
     * 판매자가 직접 판매 완료로 종료한다. 서비스 결제를 거치지 않는 직거래를 정리하기 위한 출구다.
     *
     * <p>판매 중이거나 숨겨 둔 매물에서만 허용한다. RESERVED 이후는 구매자가 이미 결제·검수 절차에
     * 들어가 있어, 판매자가 임의로 종료하면 진행 중인 주문과 상태가 어긋난다 — 그 경우는 주문 취소나
     * 환불 흐름으로 처리해야 한다.
     */
    public void markSoldBySeller() {
        if (status != ListingStatus.ON_SALE && status != ListingStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        }
        this.status = ListingStatus.SOLD;
    }

    /**
     * ON_SALE 매물을 구매자에게 예약 처리한다. 예약 유예 만료 시각은 Entity가 직접 계산하지 않고
     * 호출자(ListingService)가 Clock 기반으로 계산해 전달한다 — 테스트에서 시간을 결정적으로
     * 제어하기 위함이다.
     */
    public void reserve(Long buyerId, LocalDateTime reservedUntil) {
        requireStatus(ListingStatus.ON_SALE, ErrorCode.LISTING_NOT_ON_SALE);
        this.buyerId = buyerId;
        this.status = ListingStatus.RESERVED;
        this.reservedAt = LocalDateTime.now();
        this.reservedUntil = reservedUntil;
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
        this.reservedUntil = null;
        this.status = ListingStatus.ON_SALE;
    }

    /**
     * 결제 확정 시점에 이 예약이 여전히 같은 구매자의 유효한 예약인지 함께 검증한다. 스케줄러가
     * 만료 처리를 하기 전에 다른 구매자가 새로 예약했거나 유예 시간이 지난 상태에서 뒤늦게 결제가
     * 확정되면, 엉뚱한 예약을 결제완료로 덮어쓰지 않고 명시적으로 거부한다.
     */
    public void markPaid(Long buyerId, LocalDateTime now) {
        requireStatus(ListingStatus.RESERVED, ErrorCode.LISTING_NOT_RESERVED);
        if (!buyerId.equals(this.buyerId) || this.reservedUntil == null || this.reservedUntil.isBefore(now)) {
            throw new BusinessException(ErrorCode.LISTING_RESERVATION_MISMATCH);
        }
        this.status = ListingStatus.PAID;
        this.paidAt = now;
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
