package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DeviceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManufacturerTests {

    /**
     * 이 값은 {@code GET /api/v1/device-models}의 manufacturerId로 이미 프론트에 노출돼 있고
     * 마이그레이션(V20260727, V20260813)이 SQL {@code CRC32(LOWER(TRIM(name)))}로 같은 값을
     * 만든다. 기존 Category가 계산하던 값과 어긋나면 이관 시점에 제조사 필터가 조용히 깨진다.
     */
    @Test
    void derivesIdIdenticallyToTheLegacyCategoryCalculation() {
        Category legacy =
                Category.createLeaf(
                        Category.createTopLevel("일반형 스마트폰", DeviceType.SMARTPHONE, 1),
                        "Galaxy S24",
                        DeviceType.SMARTPHONE,
                        "Samsung",
                        OsFamily.ANDROID,
                        "SM-S921N",
                        List.of(256),
                        1);

        assertThat(Manufacturer.idOf("Samsung")).isEqualTo(legacy.getManufacturerId());
    }

    @Test
    void ignoresCaseAndSurroundingSpaceWhenDerivingId() {
        assertThat(Manufacturer.idOf("  SAMSUNG ")).isEqualTo(Manufacturer.idOf("samsung"));
    }

    @Test
    void returnsNullIdForBlankName() {
        assertThat(Manufacturer.idOf("  ")).isNull();
        assertThat(Manufacturer.idOf(null)).isNull();
    }

    @Test
    void createsWithNormalizedNameAndDerivedId() {
        Manufacturer manufacturer = Manufacturer.create(" Samsung ");

        assertThat(manufacturer.getName()).isEqualTo("Samsung");
        assertThat(manufacturer.getNormalizedName()).isEqualTo("samsung");
        assertThat(manufacturer.getId()).isEqualTo(Manufacturer.idOf("samsung"));
        assertThat(manufacturer.isActive()).isTrue();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> Manufacturer.create(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
