package com.c203.limit.domain.product.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 승인된 기기 모델. DDL: device_model
 *
 * <p>model_id는 이관 시점에 기존 리프 category.id를 그대로 물려받는다. 프론트가 쓰는
 * deviceModelId와 이미 저장된 listing.category_id가 전부 그 값이기 때문이다.
 *
 * <p>manufacturer가 null일 수 있다. '기타 (직접 입력)' 모델은 제조사가 정해지지 않은 채로
 * 존재해야 하며, 실제 제조사는 매물의 custom_manufacturer에 남는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_model")
public class DeviceModel extends BaseTimeEntity {

    /**
     * 채번하지 않고 대응하는 리프 {@code category.id}를 그대로 받는다.
     *
     * <p>이 테이블은 아직 {@code category} 리프의 사본이고, 두 id가 같다는 것이 전제다
     * ({@code listing.device_model_id}에 {@code listing.category_id}와 같은 값이 들어가고
     * FK가 걸려 있다). IDENTITY로 채번하면 새로 승인된 모델의 두 id가 어긋나 그 모델로는
     * 상품 등록이 FK 위반으로 실패한다. 종속 관계를 매핑에 드러낸다.
     */
    @Id
    @Column(name = "model_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private DeviceCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id")
    private Manufacturer manufacturer;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "normalized_model_name", nullable = false, length = 100)
    private String normalizedModelName;

    @Column(name = "model_code", nullable = false, length = 50)
    private String modelCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "os_family", length = 30)
    private OsFamily osFamily;

    @Column(name = "release_year")
    private Short releaseYear;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private DeviceModelReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private DeviceModelSourceType sourceType;

    @Column(name = "reported_by_member_id")
    private Long reportedByMemberId;

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "disabled_by_admin_id")
    private Long disabledByAdminId;

    @Column(name = "disable_reason", length = 500)
    private String disableReason;

    @Column(name = "replacement_model_id")
    private Long replacementModelId;

    /**
     * @param id 대응하는 리프 {@code category.id}. 채번하지 않는 이유는 {@link #id} 주석 참고.
     */
    public static DeviceModel create(
            Long id,
            DeviceCategory category,
            Manufacturer manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            Short releaseYear,
            int displayOrder) {
        if (id == null) throw new IllegalArgumentException("model id must not be null");
        if (category == null) throw new IllegalArgumentException("category must not be null");
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("model name must not be blank");
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("model code must not be blank");
        }
        DeviceModel model = new DeviceModel();
        model.id = id;
        model.category = category;
        model.manufacturer = manufacturer;
        model.modelName = modelName.trim();
        model.normalizedModelName = normalizeModelName(modelName);
        model.modelCode = modelCode.trim();
        model.osFamily = osFamily;
        model.releaseYear = releaseYear;
        model.isActive = true;
        model.displayOrder = displayOrder;
        model.reviewStatus = DeviceModelReviewStatus.VERIFIED;
        model.sourceType = DeviceModelSourceType.CATALOG;
        return model;
    }

    public static DeviceModel createReported(
            Long id,
            DeviceCategory category,
            Manufacturer manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            int displayOrder,
            Long memberId) {
        DeviceModel model = create(
                id,
                category,
                manufacturer,
                modelName,
                modelCode,
                osFamily,
                null,
                displayOrder);
        model.reviewStatus = DeviceModelReviewStatus.PENDING_REVIEW;
        model.sourceType = DeviceModelSourceType.USER_REPORT;
        model.reportedByMemberId = memberId;
        return model;
    }

    /**
     * 모델명 검색용 정규화. 마이그레이션(V20260813)의 {@code LOWER(REPLACE(TRIM(name), ' ', ''))}와
     * 같은 결과를 내야 한다. 사용자가 '갤럭시 북4'와 '갤럭시북4'를 섞어 입력해도 같은 값으로 걸린다.
     */
    public static String normalizeModelName(String modelName) {
        if (modelName == null) return null;
        return modelName.trim().replace(" ", "").toLowerCase(Locale.ROOT);
    }

    public Long manufacturerId() {
        return manufacturer == null ? null : manufacturer.getId();
    }

    public String manufacturerName() {
        return manufacturer == null ? null : manufacturer.getName();
    }

    public void deactivate() {
        this.isActive = false;
        this.reviewStatus = DeviceModelReviewStatus.DISABLED;
    }

    public void deactivate(Long adminId, String reason, Long replacementModelId) {
        deactivate();
        this.disabledAt = LocalDateTime.now();
        this.disabledByAdminId = adminId;
        this.disableReason = trimToNull(reason);
        this.replacementModelId = replacementModelId;
    }

    public void activate(Long adminId, String note) {
        this.isActive = true;
        this.reviewStatus = DeviceModelReviewStatus.VERIFIED;
        this.reviewedByAdminId = adminId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = trimToNull(note);
        this.disabledAt = null;
        this.disabledByAdminId = null;
        this.disableReason = null;
        this.replacementModelId = null;
    }

    public void updateCatalog(
            DeviceCategory category,
            Manufacturer manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily) {
        if (category == null || modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("category and model name are required");
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("model code is required");
        }
        this.category = category;
        this.manufacturer = manufacturer;
        this.modelName = modelName.trim();
        this.normalizedModelName = normalizeModelName(modelName);
        this.modelCode = modelCode.trim();
        this.osFamily = osFamily;
    }

    public void completeReview(Long adminId, String note) {
        this.reviewStatus = DeviceModelReviewStatus.VERIFIED;
        this.reviewedByAdminId = adminId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = trimToNull(note);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
