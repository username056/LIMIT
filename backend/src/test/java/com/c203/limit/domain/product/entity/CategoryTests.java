package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DeviceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryTests {

    @Test
    void createsLeafWithStableManufacturerIdAndNormalizedStorageOptions() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);

        Category category = Category.createLeaf(
                parent,
                "Galaxy S24",
                DeviceType.SMARTPHONE,
                " Samsung ",
                OsFamily.ANDROID,
                "SM-S921N",
                List.of(512, 128, 256, 256),
                1);

        assertThat(category.getManufacturerId()).isEqualTo(3791547856L);
        assertThat(category.getSupportedStorageGb()).isEqualTo("128,256,512");
    }

    @Test
    void rejectsNonPositiveStorageOption() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);

        assertThatThrownBy(() -> Category.createLeaf(
                        parent,
                        "Galaxy S24",
                        DeviceType.SMARTPHONE,
                        "Samsung",
                        OsFamily.ANDROID,
                        "SM-S921N",
                        List.of(0, 256),
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
