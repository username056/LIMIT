package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DeviceType;
import org.junit.jupiter.api.Test;

class DeviceModelTests {

    private static DeviceCategory category() {
        return DeviceCategory.create(DeviceType.SMARTPHONE, "스마트폰", 1);
    }

    private static DeviceModel catalogModel() {
        return DeviceModel.create(
                202L,
                category(),
                Manufacturer.create("Samsung"),
                "Galaxy S25",
                "SM-S931N",
                OsFamily.ANDROID,
                (short) 2025,
                1);
    }

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

    @Test
    void createTrimsNamesAndStartsAsActiveVerifiedCatalogModel() {
        DeviceModel model = DeviceModel.create(
                301L,
                category(),
                Manufacturer.create("Samsung"),
                "  갤럭시 북4  ",
                "  NT960XGK  ",
                OsFamily.WINDOWS,
                (short) 2024,
                3);

        assertThat(model.getId()).isEqualTo(301L);
        assertThat(model.getModelName()).isEqualTo("갤럭시 북4");
        assertThat(model.getNormalizedModelName()).isEqualTo("갤럭시북4");
        assertThat(model.getModelCode()).isEqualTo("NT960XGK");
        assertThat(model.isActive()).isTrue();
        assertThat(model.getReviewStatus()).isEqualTo(DeviceModelReviewStatus.VERIFIED);
        assertThat(model.getSourceType()).isEqualTo(DeviceModelSourceType.CATALOG);
        assertThat(model.getReleaseYear()).isEqualTo((short) 2024);
    }

    @Test
    void createRejectsMissingId() {
        assertThatThrownBy(() -> DeviceModel.create(
                        null,
                        category(),
                        Manufacturer.create("Samsung"),
                        "Galaxy S25",
                        "SM-S931N",
                        OsFamily.ANDROID,
                        (short) 2025,
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsMissingCategory() {
        assertThatThrownBy(() -> DeviceModel.create(
                        202L,
                        null,
                        Manufacturer.create("Samsung"),
                        "Galaxy S25",
                        "SM-S931N",
                        OsFamily.ANDROID,
                        (short) 2025,
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsNullOrBlankModelName() {
        assertThatThrownBy(() -> DeviceModel.create(
                        202L,
                        category(),
                        null,
                        null,
                        "SM-S931N",
                        OsFamily.ANDROID,
                        (short) 2025,
                        1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DeviceModel.create(
                        202L,
                        category(),
                        null,
                        "   ",
                        "SM-S931N",
                        OsFamily.ANDROID,
                        (short) 2025,
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsNullOrBlankModelCode() {
        assertThatThrownBy(() -> DeviceModel.create(
                        202L,
                        category(),
                        null,
                        "Galaxy S25",
                        null,
                        OsFamily.ANDROID,
                        (short) 2025,
                        1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DeviceModel.create(
                        202L,
                        category(),
                        null,
                        "Galaxy S25",
                        "   ",
                        OsFamily.ANDROID,
                        (short) 2025,
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAllowsNullManufacturerForDirectInputModel() {
        DeviceModel model = DeviceModel.create(
                999L,
                category(),
                null,
                "기타 (직접 입력)",
                "ETC",
                null,
                null,
                99);

        assertThat(model.manufacturerId()).isNull();
        assertThat(model.manufacturerName()).isNull();
        assertThat(model.getOsFamily()).isNull();
        assertThat(model.getReleaseYear()).isNull();
    }

    @Test
    void manufacturerAccessorsDelegateToManufacturerWhenPresent() {
        Manufacturer manufacturer = Manufacturer.create("Samsung");
        DeviceModel model = DeviceModel.create(
                202L,
                category(),
                manufacturer,
                "Galaxy S25",
                "SM-S931N",
                OsFamily.ANDROID,
                (short) 2025,
                1);

        assertThat(model.manufacturerId()).isEqualTo(manufacturer.getId());
        assertThat(model.manufacturerName()).isEqualTo("Samsung");
    }

    @Test
    void normalizeModelNameReturnsNullForNullInput() {
        assertThat(DeviceModel.normalizeModelName(null)).isNull();
        assertThat(DeviceModel.normalizeModelName("  Galaxy Book 4  ")).isEqualTo("galaxybook4");
    }

    @Test
    void createReportedMarksModelAsPendingUserReportWithoutReleaseYear() {
        DeviceModel model = DeviceModel.createReported(
                777L,
                category(),
                Manufacturer.create("Apple"),
                "iPhone 17",
                "A3300",
                OsFamily.IOS,
                5,
                42L);

        assertThat(model.getReviewStatus()).isEqualTo(DeviceModelReviewStatus.PENDING_REVIEW);
        assertThat(model.getSourceType()).isEqualTo(DeviceModelSourceType.USER_REPORT);
        assertThat(model.getReportedByMemberId()).isEqualTo(42L);
        assertThat(model.getReleaseYear()).isNull();
        assertThat(model.isActive()).isTrue();
    }

    @Test
    void createReportedAppliesSameValidationAsCreate() {
        assertThatThrownBy(() -> DeviceModel.createReported(
                        777L,
                        category(),
                        null,
                        " ",
                        "A3300",
                        OsFamily.IOS,
                        5,
                        42L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivateWithoutAuditKeepsAuditFieldsEmpty() {
        DeviceModel model = catalogModel();

        model.deactivate();

        assertThat(model.isActive()).isFalse();
        assertThat(model.getReviewStatus()).isEqualTo(DeviceModelReviewStatus.DISABLED);
        assertThat(model.getDisabledAt()).isNull();
        assertThat(model.getDisabledByAdminId()).isNull();
        assertThat(model.getDisableReason()).isNull();
    }

    @Test
    void deactivateStoresBlankReasonAsNull() {
        DeviceModel model = catalogModel();

        model.deactivate(9L, "   ", null);

        assertThat(model.getDisableReason()).isNull();
        assertThat(model.getReplacementModelId()).isNull();
        assertThat(model.getDisabledAt()).isNotNull();

        DeviceModel other = catalogModel();
        other.deactivate(9L, null, null);

        assertThat(other.getDisableReason()).isNull();
    }

    @Test
    void activateStoresBlankNoteAsNull() {
        DeviceModel model = catalogModel();
        model.deactivate(9L, "중복", 1L);

        model.activate(10L, "  ");

        assertThat(model.getReviewNote()).isNull();
        assertThat(model.getReviewedByAdminId()).isEqualTo(10L);
        assertThat(model.getReviewedAt()).isNotNull();
    }

    @Test
    void updateCatalogReplacesReferencesAndRenormalizesName() {
        DeviceModel model = catalogModel();
        DeviceCategory newCategory = DeviceCategory.create(DeviceType.LAPTOP, "노트북", 2);
        Manufacturer newManufacturer = Manufacturer.create("Apple");

        model.updateCatalog(
                newCategory, newManufacturer, "  MacBook Air 15  ", "  MC01  ", OsFamily.MACOS);

        assertThat(model.getCategory()).isSameAs(newCategory);
        assertThat(model.manufacturerName()).isEqualTo("Apple");
        assertThat(model.getModelName()).isEqualTo("MacBook Air 15");
        assertThat(model.getNormalizedModelName()).isEqualTo("macbookair15");
        assertThat(model.getModelCode()).isEqualTo("MC01");
        assertThat(model.getOsFamily()).isEqualTo(OsFamily.MACOS);
    }

    @Test
    void updateCatalogAllowsClearingManufacturer() {
        DeviceModel model = catalogModel();

        model.updateCatalog(category(), null, "기타 (직접 입력)", "ETC", null);

        assertThat(model.manufacturerId()).isNull();
        assertThat(model.manufacturerName()).isNull();
    }

    @Test
    void updateCatalogRejectsMissingCategoryOrModelName() {
        DeviceModel model = catalogModel();

        assertThatThrownBy(
                        () -> model.updateCatalog(
                                null, null, "Galaxy S25", "SM-S931N", OsFamily.ANDROID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> model.updateCatalog(
                                category(), null, null, "SM-S931N", OsFamily.ANDROID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> model.updateCatalog(
                                category(), null, "  ", "SM-S931N", OsFamily.ANDROID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateCatalogRejectsMissingModelCode() {
        DeviceModel model = catalogModel();

        assertThatThrownBy(
                        () -> model.updateCatalog(
                                category(), null, "Galaxy S25", null, OsFamily.ANDROID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> model.updateCatalog(
                                category(), null, "Galaxy S25", "   ", OsFamily.ANDROID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completeReviewVerifiesReportedModelAndTrimsNote() {
        DeviceModel model = DeviceModel.createReported(
                777L,
                category(),
                Manufacturer.create("Apple"),
                "iPhone 17",
                "A3300",
                OsFamily.IOS,
                5,
                42L);

        model.completeReview(11L, "  확인 완료  ");

        assertThat(model.getReviewStatus()).isEqualTo(DeviceModelReviewStatus.VERIFIED);
        assertThat(model.getReviewedByAdminId()).isEqualTo(11L);
        assertThat(model.getReviewNote()).isEqualTo("확인 완료");
        assertThat(model.getReviewedAt()).isNotNull();
    }

    @Test
    void completeReviewStoresMissingNoteAsNull() {
        DeviceModel model = catalogModel();

        model.completeReview(11L, null);

        assertThat(model.getReviewNote()).isNull();
    }
}
