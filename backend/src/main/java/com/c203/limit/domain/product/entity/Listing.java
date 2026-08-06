package com.c203.limit.domain.product.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 중고 전자기기 매물(상품). DDL: listing. seller_id/buyer_id는 회원 도메인 참조라 FK 없이 ID만 보관한다.
 */
@Entity
@Getter
@DynamicUpdate
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

    /**
     * 새 카탈로그의 모델 참조. 이관 시점에 device_model.model_id가 리프 category.id를 그대로
     * 물려받았으므로 기존 매물은 category_id와 같은 값을 갖는다. 카탈로그 전환이 끝나면
     * {@link #category}를 걷어내고 이 열만 남긴다.
     */
    @Column(name = "device_model_id")
    private Long deviceModelId;

    /** 선택한 실제 판매 조합. 등록 화면 개편(4단계) 전까지는 채워지지 않는다. */
    @Column(name = "device_variant_id")
    private Long deviceVariantId;

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

    @Column(name = "screen_size_inches", precision = 4, scale = 2)
    private BigDecimal screenSizeInches;

    @Column(name = "memory_gb")
    private Integer memoryGb;

    @Column(name = "connectivity", length = 20)
    private String connectivity;

    /**
     * 등록 시점에 선택한 사양을 얼려 둔 JSON. 카탈로그가 나중에 수정돼도 이미 등록된 매물이
     * 어떤 사양으로 팔렸는지는 바뀌지 않아야 한다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spec_snapshot")
    private String specSnapshot;

    /** 공개 상세 조회수. 일반 엔티티 수정이 원자 증가 결과를 덮어쓰지 않도록 동적 UPDATE를 사용한다. */
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "trade_region", length = 100)
    private String tradeRegion;

    @Column(name = "checklist_template_id", nullable = false)
    private Long checklistTemplateId;

    @Column(name = "precheck_completed", nullable = false)
    private boolean precheckCompleted;

    @Column(name = "draft_step", nullable = false)
    private int draftStep;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "web_device_check_results")
    private Map<TestType, DeviceCheckResult> webDeviceCheckResults;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 30)
    private ListingModerationStatus moderationStatus;

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
        listing.moderationStatus = ListingModerationStatus.NORMAL;
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

    /** 카탈로그에 없는 기기를 직접 입력해 등록했는지 여부. */
    public boolean hasCustomModel() {
        return customManufacturer != null && customModelName != null;
    }

    /**
     * 선택 기능 체크리스트 항목을 append하기 위해 매물 전용 DRAFT 템플릿으로 전환하거나 교체한다.
     * 등록 시점에 선택 기능이 없어 카테고리 공유 PUBLISHED 템플릿을 쓰던 매물이, 수정 화면에서 처음
     * 기능을 선택하는 순간 이 메서드로 전용 템플릿을 갖게 된다.
     */
    public void changeChecklistTemplate(Long checklistTemplateId) {
        this.checklistTemplateId = checklistTemplateId;
    }

    /**
     * 등록 시점의 카탈로그 참조와 사양 스냅샷을 확정한다.
     *
     * <p>스냅샷은 한 번 정해지면 카탈로그가 바뀌어도 따라 변하지 않는다. 판매자가 상품 정보를
     * 고치는 경로({@link #updateBySeller})에서는 건드리지 않는다 — 그 경로가 스냅샷을 다시 쓰면
     * '등록 시점 사양을 보존한다'는 성질이 사라진다.
     */
    public void applyCatalogSelection(
            Long deviceModelId, Long deviceVariantId, String specSnapshot) {
        this.deviceModelId = deviceModelId;
        this.deviceVariantId = deviceVariantId;
        this.specSnapshot = specSnapshot;
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

    public void updateWebDeviceCheckResults(Map<TestType, DeviceCheckResult> results) {
        this.webDeviceCheckResults = results == null ? null : new LinkedHashMap<>(results);
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
     * 직접 판매 완료로 닫은 매물을 다시 판매 중으로 되돌린다. 직거래는 약속이 깨질 수 있어,
     * 판매자가 상품을 새로 등록하지 않고 원래 글로 돌아갈 길이 필요하다.
     *
     * <p>서비스 결제를 거쳐 종료된 매물(SETTLED 등)은 대상이 아니다. 그 거래는 주문·정산 기록이
     * 남아 있어 매물 상태만 되돌리면 서로 맞지 않는다.
     */
    public void reopenSoldBySeller() {
        requireStatus(ListingStatus.SOLD, ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION);
        this.status = ListingStatus.ON_SALE;
    }

    /**
     * ON_SALE 매물을 구매자에게 예약 처리한다. 예약 유예 만료 시각은 Entity가 직접 계산하지 않고
     * 호출자(ListingService)가 Clock 기반으로 계산해 전달한다 — 테스트에서 시간을 결정적으로
     * 제어하기 위함이다.
     */
    public void reserve(Long buyerId, LocalDateTime reservedUntil) {
        requireStatus(ListingStatus.ON_SALE, ErrorCode.LISTING_NOT_ON_SALE);
        if (!isPubliclyVisible()) {
            throw new BusinessException(ErrorCode.LISTING_MODERATION_BLOCKED);
        }
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

    /**
     * PG 대사(reconcile)로 Toss가 이미 승인했음을 확인한 결제를 복구할 때 호출한다.
     * {@link #markPaid(Long, LocalDateTime)}와 달리 예약 유예 시간(reservedUntil)이 이미 지났어도
     * 허용한다 — 대사 자체가 "시간과 무관하게 PG가 승인했다"는 증거이기 때문이다. 다만 예약 주체
     * (buyerId)는 여전히 이 결제의 구매자와 일치해야 한다. 그 사이 다른 구매자에게 재배정됐다면
     * 자동 복구 대상이 아니므로 그대로 거부한다.
     */
    public void markPaidRecoveredFromPg(Long buyerId, LocalDateTime now) {
        requireStatus(ListingStatus.RESERVED, ErrorCode.LISTING_NOT_RESERVED);
        if (!buyerId.equals(this.buyerId) || this.reservedUntil == null) {
            throw new BusinessException(ErrorCode.LISTING_RESERVATION_MISMATCH);
        }
        this.status = ListingStatus.PAID;
        this.paidAt = now;
    }

    /**
     * 같은 구매자가 결제창을 다시 열 때(명시적 재시도든, 상품 페이지에서 다시 구매하기를 눌러 기존
     * 예약을 이어받든) 예약 유예 시간을 지금부터 다시 계산해 늘려준다. 예약 주체가 다르면(다른
     * 구매자 소유) 거부한다 — 이 매물의 예약을 남의 요청으로 늘릴 수는 없다.
     */
    public void renewReservationForBuyer(Long buyerId, LocalDateTime reservedUntil) {
        requireStatus(ListingStatus.RESERVED, ErrorCode.LISTING_NOT_RESERVED);
        if (!buyerId.equals(this.buyerId)) {
            throw new BusinessException(ErrorCode.LISTING_RESERVATION_MISMATCH);
        }
        this.reservedUntil = reservedUntil;
    }

    /**
     * 결제 완료 즉시 검수 단계로 전환한다. 이 서비스에는 판매자의 별도 "전달완료" 액션이 없어서,
     * 결제 확정 시점을 handedOverAt으로 기록하고 그 시점 기준으로 자동 구매확정 기한
     * (autoConfirmAt)을 함께 계산해 저장한다.
     */
    public void enterInspection(LocalDateTime handedOverAt, LocalDateTime autoConfirmAt) {
        requireStatus(ListingStatus.PAID, ErrorCode.LISTING_NOT_PAID);
        this.status = ListingStatus.INSPECTING;
        this.handedOverAt = handedOverAt;
        this.autoConfirmAt = autoConfirmAt;
    }

    /**
     * 검수 단계의 매물을 구매확정으로 전환한다. buyerId가 주어지면(구매자 본인 요청) 예약 주체와
     * 일치하는지 검증하고, null이면(자동 구매확정 스케줄러가 기한 경과로 호출) 검증 없이 진행한다.
     */
    public void confirm(Long buyerId, LocalDateTime now) {
        requireStatus(ListingStatus.INSPECTING, ErrorCode.LISTING_NOT_INSPECTING);
        if (buyerId != null && !buyerId.equals(this.buyerId)) {
            throw new BusinessException(ErrorCode.LISTING_RESERVATION_MISMATCH);
        }
        this.status = ListingStatus.CONFIRMED;
        this.confirmedAt = now;
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

    public boolean isPubliclyVisible() {
        return status == ListingStatus.ON_SALE
                && (moderationStatus == ListingModerationStatus.NORMAL
                        || moderationStatus == ListingModerationStatus.WARNING_ACK_REQUIRED);
    }

    public void issueModerationWarning(String reason) {
        requireModeratableStatus();
        moderationStatus = ListingModerationStatus.WARNING_ACK_REQUIRED;
        suspendedReason = trimToNull(reason);
    }

    public void acknowledgeModerationWarning() {
        if (moderationStatus != ListingModerationStatus.WARNING_ACK_REQUIRED) {
            throw new BusinessException(ErrorCode.MODERATION_STATE_CONFLICT);
        }
        moderationStatus = ListingModerationStatus.NORMAL;
        suspendedReason = null;
    }

    public void suspendForModeration(String reason) {
        requireModeratableStatus();
        moderationStatus = ListingModerationStatus.SUSPENDED;
        suspendedReason = trimToNull(reason);
    }

    public void requestModerationRestoration() {
        if (moderationStatus != ListingModerationStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.MODERATION_STATE_CONFLICT);
        }
        moderationStatus = ListingModerationStatus.RESTORE_REQUESTED;
    }

    public void approveModerationRestoration() {
        if (moderationStatus != ListingModerationStatus.RESTORE_REQUESTED) {
            throw new BusinessException(ErrorCode.MODERATION_STATE_CONFLICT);
        }
        moderationStatus = ListingModerationStatus.NORMAL;
        suspendedReason = null;
    }

    public void rejectModerationRestoration(String reason) {
        if (moderationStatus != ListingModerationStatus.RESTORE_REQUESTED) {
            throw new BusinessException(ErrorCode.MODERATION_STATE_CONFLICT);
        }
        moderationStatus = ListingModerationStatus.SUSPENDED;
        String normalized = trimToNull(reason);
        if (normalized != null) suspendedReason = normalized;
    }

    private void requireModeratableStatus() {
        if (status != ListingStatus.ON_SALE && status != ListingStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.MODERATION_STATE_CONFLICT);
        }
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
