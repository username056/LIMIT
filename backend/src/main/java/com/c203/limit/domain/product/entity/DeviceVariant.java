package com.c203.limit.domain.product.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실제 판매되는 SKU 조합. DDL: device_variant
 *
 * <p>색상·용량을 각각 독립된 목록으로 두면 존재하지 않는 조합(예: 512GB만 나오는 색상에
 * 128GB)을 막을 수 없다. 조합 자체를 한 행으로 저장하고, 등록 화면은 앞선 선택으로 좁혀진
 * 행 집합에서 다음 축의 값을 뽑아 보여준다.
 *
 * <p>축 열이 전부 nullable인 이유는 카테고리마다 의미 있는 축이 다르기 때문이다. 노트북은
 * cpu/gpu/memoryGb를, 스마트폰은 color/storageGb를 쓴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_variant")
public class DeviceVariant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private DeviceModel model;

    @Column(name = "variant_key", nullable = false, length = 120)
    private String variantKey;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "storage_gb")
    private Integer storageGb;

    @Column(name = "memory_gb")
    private Integer memoryGb;

    @Column(name = "screen_size_inches", precision = 4, scale = 2)
    private BigDecimal screenSizeInches;

    @Column(name = "cpu", length = 100)
    private String cpu;

    @Column(name = "gpu", length = 100)
    private String gpu;

    @Column(name = "weight_kg", precision = 5, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "connectivity", length = 20)
    private String connectivity;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public static DeviceVariant create(
            DeviceModel model, String variantKey, String displayName) {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        if (variantKey == null || variantKey.isBlank()) {
            throw new IllegalArgumentException("variant key must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display name must not be blank");
        }
        DeviceVariant variant = new DeviceVariant();
        variant.model = model;
        variant.variantKey = variantKey.trim();
        variant.displayName = displayName.trim();
        variant.isActive = true;
        return variant;
    }

    public DeviceVariant withStorage(Integer storageGb) {
        this.storageGb = storageGb;
        return this;
    }

    public DeviceVariant withColor(String color) {
        this.color = color;
        return this;
    }

    public DeviceVariant withLaptopSpecs(
            String cpu,
            String gpu,
            Integer memoryGb,
            BigDecimal screenSizeInches,
            BigDecimal weightKg) {
        this.cpu = cpu;
        this.gpu = gpu;
        this.memoryGb = memoryGb;
        this.screenSizeInches = screenSizeInches;
        this.weightKg = weightKg;
        return this;
    }

    public DeviceVariant withConnectivity(String connectivity) {
        this.connectivity = connectivity;
        return this;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
