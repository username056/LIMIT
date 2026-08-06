package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DeviceType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryTests {

    private static Category leaf(Category parent) {
        return Category.createLeaf(
                parent,
                "Galaxy S24",
                DeviceType.SMARTPHONE,
                "Samsung",
                OsFamily.ANDROID,
                "SM-S921N",
                List.of(256),
                1);
    }

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

    @Test
    void rejectsNullStorageOptionElement() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);

        assertThatThrownBy(() -> Category.createLeaf(
                        parent,
                        "Galaxy S24",
                        DeviceType.SMARTPHONE,
                        "Samsung",
                        OsFamily.ANDROID,
                        "SM-S921N",
                        Arrays.asList(256, null),
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leavesStorageOptionsEmptyWhenNotProvided() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);

        Category withNull = Category.createLeaf(
                parent,
                "Galaxy S24",
                DeviceType.SMARTPHONE,
                "Samsung",
                OsFamily.ANDROID,
                "SM-S921N",
                null,
                1);
        Category withEmpty = Category.createLeaf(
                parent,
                "Galaxy S24",
                DeviceType.SMARTPHONE,
                "Samsung",
                OsFamily.ANDROID,
                "SM-S921N",
                List.of(),
                1);

        assertThat(withNull.getSupportedStorageGb()).isNull();
        assertThat(withEmpty.getSupportedStorageGb()).isNull();
    }

    @Test
    void createsLeafWithoutManufacturerIdWhenManufacturerIsMissing() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);

        Category withoutManufacturer = Category.createLeaf(
                parent,
                "기타 (직접 입력)",
                DeviceType.SMARTPHONE,
                null,
                null,
                null,
                null,
                99);
        Category blankManufacturer = Category.createLeaf(
                parent,
                "기타 (직접 입력)",
                DeviceType.SMARTPHONE,
                "   ",
                null,
                null,
                null,
                99);

        assertThat(withoutManufacturer.getManufacturerId()).isNull();
        assertThat(blankManufacturer.getManufacturerId()).isNull();
    }

    @Test
    void createsTopLevelCategoryWithoutParentAndActive() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);

        assertThat(parent.getParent()).isNull();
        assertThat(parent.isActive()).isTrue();
        assertThat(parent.getManufacturerId()).isNull();
        assertThat(parent.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void deactivateAndActivateToggleExposure() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);

        parent.deactivate();
        assertThat(parent.isActive()).isFalse();

        parent.activate();
        assertThat(parent.isActive()).isTrue();
    }

    @Test
    void updateLeafTrimsValuesAndInheritsDeviceTypeFromParent() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        Category newParent = Category.createTopLevel("노트북", DeviceType.LAPTOP, 2);
        Category category = leaf(parent);

        category.updateLeaf(newParent, "  Galaxy Book 4  ", "  Samsung  ", OsFamily.WINDOWS, "  NT960  ");

        assertThat(category.getParent()).isSameAs(newParent);
        assertThat(category.getName()).isEqualTo("Galaxy Book 4");
        assertThat(category.getDeviceType()).isEqualTo(DeviceType.LAPTOP);
        assertThat(category.getManufacturer()).isEqualTo("Samsung");
        assertThat(category.getManufacturerId()).isEqualTo(3791547856L);
        assertThat(category.getOsFamily()).isEqualTo(OsFamily.WINDOWS);
        assertThat(category.getModelCode()).isEqualTo("NT960");
    }

    @Test
    void updateLeafKeepsExistingModelCodeWhenNewValueIsMissing() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        Category category = leaf(parent);

        category.updateLeaf(parent, "Galaxy S24 Ultra", "Samsung", OsFamily.ANDROID, null);
        assertThat(category.getModelCode()).isEqualTo("SM-S921N");

        category.updateLeaf(parent, "Galaxy S24 Ultra", "Samsung", OsFamily.ANDROID, "   ");
        assertThat(category.getModelCode()).isEqualTo("SM-S921N");
    }

    @Test
    void updateLeafRequiresActiveTopLevelParent() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        Category category = leaf(parent);
        Category nestedParent = leaf(parent);
        Category inactiveParent = Category.createTopLevel("태블릿", DeviceType.TABLET, 3);
        inactiveParent.deactivate();

        assertThatThrownBy(
                        () -> category.updateLeaf(
                                null, "Galaxy S24", "Samsung", OsFamily.ANDROID, "SM-S921N"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> category.updateLeaf(
                                nestedParent, "Galaxy S24", "Samsung", OsFamily.ANDROID, "SM-S921N"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> category.updateLeaf(
                                inactiveParent,
                                "Galaxy S24",
                                "Samsung",
                                OsFamily.ANDROID,
                                "SM-S921N"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateLeafRequiresNameAndManufacturer() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        Category category = leaf(parent);

        assertThatThrownBy(
                        () -> category.updateLeaf(
                                parent, null, "Samsung", OsFamily.ANDROID, "SM-S921N"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> category.updateLeaf(
                                parent, "   ", "Samsung", OsFamily.ANDROID, "SM-S921N"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> category.updateLeaf(
                                parent, "Galaxy S24", null, OsFamily.ANDROID, "SM-S921N"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> category.updateLeaf(
                                parent, "Galaxy S24", "   ", OsFamily.ANDROID, "SM-S921N"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
