package com.c203.limit.domain.product.entity;

import com.c203.limit.domain.inspection.enums.DeviceType;
import jakarta.persistence.*;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "os_family", length = 30)
    private OsFamily osFamily;

    @Column(name = "model_code", length = 50)
    private String modelCode;

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
            int displayOrder) {
        Category category = new Category();
        category.parent = parent;
        category.name = name;
        category.deviceType = deviceType;
        category.manufacturer = manufacturer;
        category.osFamily = osFamily;
        category.modelCode = modelCode;
        category.displayOrder = displayOrder;
        category.isActive = true;
        return category;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
