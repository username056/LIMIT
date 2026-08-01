package com.c203.limit.domain.product.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    public static DeviceModel create(
            DeviceCategory category,
            Manufacturer manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            Short releaseYear,
            int displayOrder) {
        if (category == null) throw new IllegalArgumentException("category must not be null");
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("model name must not be blank");
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("model code must not be blank");
        }
        DeviceModel model = new DeviceModel();
        model.category = category;
        model.manufacturer = manufacturer;
        model.modelName = modelName.trim();
        model.normalizedModelName = normalizeModelName(modelName);
        model.modelCode = modelCode.trim();
        model.osFamily = osFamily;
        model.releaseYear = releaseYear;
        model.isActive = true;
        model.displayOrder = displayOrder;
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
    }
}
