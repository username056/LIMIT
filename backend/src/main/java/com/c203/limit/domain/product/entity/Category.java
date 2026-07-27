package com.c203.limit.domain.product.entity;

import com.c203.limit.domain.inspection.enums.DeviceType;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기 카테고리(대분류~리프 모델까지 자기참조 트리). DDL: category
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private DeviceType deviceType;

    @Column(length = 50)
    private String manufacturer;

    @Column(name = "manufacturer_id")
    private Long manufacturerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "os_family", length = 30)
    private OsFamily osFamily;

    @Column(name = "model_code", length = 50)
    private String modelCode;

    @Column(name = "supported_storage_gb", length = 100)
    private String supportedStorageGb;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public static Category createTopLevel(String name, DeviceType deviceType, int displayOrder) {
        Category category = new Category();
        category.name = name;
        category.deviceType = deviceType;
        category.displayOrder = displayOrder;
        category.isActive = true;
        return category;
    }

    public static Category createLeaf(
            Category parent,
            String name,
            DeviceType deviceType,
            String manufacturer,
            OsFamily osFamily,
            String modelCode,
            List<Integer> supportedStorageGb,
            int displayOrder) {
        Category category = new Category();
        category.parent = parent;
        category.name = name;
        category.deviceType = deviceType;
        category.manufacturer = manufacturer;
        category.manufacturerId = manufacturerId(manufacturer);
        category.osFamily = osFamily;
        category.modelCode = modelCode;
        category.supportedStorageGb = normalizeStorage(supportedStorageGb);
        category.displayOrder = displayOrder;
        category.isActive = true;
        return category;
    }

    private static String normalizeStorage(List<Integer> supportedStorageGb) {
        if (supportedStorageGb == null || supportedStorageGb.isEmpty()) return null;
        if (supportedStorageGb.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("supported storage must contain positive values");
        }
        return supportedStorageGb.stream()
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static Long manufacturerId(String manufacturer) {
        if (manufacturer == null || manufacturer.isBlank()) return null;
        CRC32 crc32 = new CRC32();
        crc32.update(
                manufacturer.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    public void deactivate() {
        this.isActive = false;
    }
}
