package com.c203.limit.domain.product.entity;

import com.c203.limit.domain.inspection.enums.DeviceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 승인된 최상위 기기 카테고리. DDL: device_category
 *
 * <p>기존 {@link Category}가 겸하던 두 역할(카테고리/기기 모델) 중 카테고리 쪽을 떼어낸 테이블이다.
 * category_id는 이관 시점에 기존 최상위 category.id를 그대로 물려받는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_category")
public class DeviceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 30)
    private DeviceType code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public static DeviceCategory create(DeviceType code, String name, int displayOrder) {
        DeviceCategory category = new DeviceCategory();
        category.code = code;
        category.name = name;
        category.displayOrder = displayOrder;
        category.isActive = true;
        return category;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
