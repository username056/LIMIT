package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.enums.DeviceType;
import org.junit.jupiter.api.Test;

class DeviceModelTests {

    @Test
    void activationClearsPreviousDeactivationAudit() {
        DeviceModel model = DeviceModel.create(
                202L,
                DeviceCategory.create(DeviceType.SMARTPHONE, "스마트폰", 1),
                Manufacturer.create("Samsung"),
                "Galaxy S25",
                "SM-S931N",
                OsFamily.ANDROID,
                (short) 2025,
                1);

        model.deactivate(9L, "  중복 등록  ", 203L);

        assertThat(model.isActive()).isFalse();
        assertThat(model.getReviewStatus()).isEqualTo(DeviceModelReviewStatus.DISABLED);
        assertThat(model.getDisabledByAdminId()).isEqualTo(9L);
        assertThat(model.getDisableReason()).isEqualTo("중복 등록");
        assertThat(model.getReplacementModelId()).isEqualTo(203L);

        model.activate(10L, "재활성화");

        assertThat(model.isActive()).isTrue();
        assertThat(model.getReviewStatus()).isEqualTo(DeviceModelReviewStatus.VERIFIED);
        assertThat(model.getDisabledAt()).isNull();
        assertThat(model.getDisabledByAdminId()).isNull();
        assertThat(model.getDisableReason()).isNull();
        assertThat(model.getReplacementModelId()).isNull();
    }
}
