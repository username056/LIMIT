package com.c203.limit.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제조사. DDL: manufacturer
 *
 * <p>PK를 채번하지 않고 {@link #idOf(String)}(정규화 이름의 CRC32)를 그대로 쓴다. 기존
 * category.manufacturer_id가 같은 방식으로 계산된 값이고 {@code GET /api/v1/device-models}의
 * manufacturerId 파라미터로 이미 노출돼 있어, 새 시퀀스를 도입하면 이관 시점에 기존 클라이언트의
 * 제조사 필터가 조용히 깨진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "manufacturer")
public class Manufacturer {

    @Id
    @Column(name = "manufacturer_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 50)
    private String normalizedName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public static Manufacturer create(String name) {
        String normalized = normalize(name);
        if (normalized == null) {
            throw new IllegalArgumentException("manufacturer name must not be blank");
        }
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.id = idOf(name);
        manufacturer.name = name.trim();
        manufacturer.normalizedName = normalized;
        manufacturer.isActive = true;
        return manufacturer;
    }

    /**
     * 제조사 이름으로 식별자를 계산한다. MySQL {@code CRC32(LOWER(TRIM(name)))}와 동일한 값을
     * 내야 한다 — 마이그레이션(V20260727, V20260813)이 SQL 쪽에서 같은 식을 쓴다.
     */
    public static Long idOf(String name) {
        String normalized = normalize(name);
        if (normalized == null) return null;
        CRC32 crc32 = new CRC32();
        crc32.update(normalized.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    /** CRC32 식별자가 같을 때 실제로도 같은 정규화 이름인지 확인한다. */
    public boolean hasSameNormalizedName(String name) {
        return normalizedName.equals(normalize(name));
    }

    private static String normalize(String name) {
        if (name == null || name.isBlank()) return null;
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public void deactivate() {
        this.isActive = false;
    }
}
