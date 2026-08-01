package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.dto.response.DeviceModelOptionsResponse;
import com.c203.limit.domain.product.dto.response.DeviceVariantAxisResponse;
import com.c203.limit.domain.product.entity.DeviceCategory;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceVariant;
import com.c203.limit.domain.product.entity.DeviceVariantAxis;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceVariantRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceCatalogOptionServiceTests {

    private static final long MODEL_ID = 11L;

    @Mock DeviceModelRepository modelRepository;
    @Mock DeviceVariantRepository variantRepository;
    DeviceCatalogOptionService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCatalogOptionService(modelRepository, variantRepository);
    }

    @Test
    void listsEveryAxisValueWhenNothingSelected() {
        givenCatalog(
                variant("S24-256-BLACK", "블랙", 256),
                variant("S24-512-BLACK", "블랙", 512),
                variant("S24-256-GRAY", "그레이", 256));

        DeviceModelOptionsResponse response = service.options(MODEL_ID, Map.of());

        assertThat(values(response, DeviceVariantAxis.COLOR))
                .containsExactly("그레이", "블랙");
        assertThat(values(response, DeviceVariantAxis.STORAGE_GB))
                .containsExactly("256", "512");
        assertThat(response.matchedVariants()).hasSize(3);
        assertThat(response.selectedVariantId()).isNull();
    }

    /** 기획의 핵심 규칙 — 존재하지 않는 색상x용량 조합이 선택지에 남아서는 안 된다. */
    @Test
    void narrowsNextAxisToCombinationsThatActuallyExist() {
        givenCatalog(
                variant("S24-256-BLACK", "블랙", 256),
                variant("S24-512-BLACK", "블랙", 512),
                variant("S24-256-GRAY", "그레이", 256));

        DeviceModelOptionsResponse response =
                service.options(MODEL_ID, selection(DeviceVariantAxis.COLOR, "그레이"));

        assertThat(values(response, DeviceVariantAxis.STORAGE_GB)).containsExactly("256");
        assertThat(response.matchedVariants()).hasSize(1);
        assertThat(axis(response, DeviceVariantAxis.COLOR).selected()).isEqualTo("그레이");
    }

    /**
     * 색상 축의 후보는 색상 선택 자체로 좁히지 않는다. 좁히면 이미 고른 값 하나만 남아 사용자가
     * 선택을 되돌릴 수 없다.
     */
    @Test
    void keepsOtherValuesSelectableOnTheAxisAlreadyChosen() {
        givenCatalog(
                variant("S24-256-BLACK", "블랙", 256),
                variant("S24-512-BLACK", "블랙", 512),
                variant("S24-256-GRAY", "그레이", 256));

        DeviceModelOptionsResponse response =
                service.options(MODEL_ID, selection(DeviceVariantAxis.COLOR, "그레이"));

        assertThat(values(response, DeviceVariantAxis.COLOR)).containsExactly("그레이", "블랙");
    }

    @Test
    void resolvesVariantWhenSelectionNarrowsToOne() {
        givenCatalog(
                variant("S24-256-BLACK", "블랙", 256), variant("S24-512-BLACK", "블랙", 512));

        Map<DeviceVariantAxis, String> selection = selection(DeviceVariantAxis.COLOR, "블랙");
        selection.put(DeviceVariantAxis.STORAGE_GB, "512");

        assertThat(service.options(MODEL_ID, selection).selectedVariantId()).isNotNull();
    }

    @Test
    void hidesAxesThatThisCategoryDoesNotUse() {
        givenCatalog(variant("S24-256-BLACK", "블랙", 256));

        DeviceModelOptionsResponse response = service.options(MODEL_ID, Map.of());

        assertThat(response.axes())
                .extracting(DeviceVariantAxisResponse::axis)
                .containsExactlyInAnyOrder(
                        DeviceVariantAxis.COLOR.name(), DeviceVariantAxis.STORAGE_GB.name());
    }

    /** DECIMAL(4,2)로 읽힌 15.60과 사용자가 보낸 15.6은 같은 화면 크기다. */
    @Test
    void matchesDecimalAxisByValueNotByText() {
        DeviceVariant laptop = variant("BOOK4-15", null, 512);
        laptop.withLaptopSpecs("Intel Core 5", null, 16, new BigDecimal("15.60"), null);
        givenCatalog(laptop);

        DeviceModelOptionsResponse response =
                service.options(MODEL_ID, selection(DeviceVariantAxis.SCREEN_SIZE_INCHES, "15.6"));

        assertThat(response.matchedVariants()).hasSize(1);
        assertThat(values(response, DeviceVariantAxis.SCREEN_SIZE_INCHES)).containsExactly("15.6");
    }

    @Test
    void rejectsSelectionThatMatchesNoCombination() {
        givenCatalog(variant("S24-256-BLACK", "블랙", 256));

        assertThatThrownBy(
                        () ->
                                service.options(
                                        MODEL_ID, selection(DeviceVariantAxis.COLOR, "핑크")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void rejectsUnknownModel() {
        when(modelRepository.findWithCatalogById(MODEL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.options(MODEL_ID, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_MODEL_NOT_FOUND);
    }

    private void givenCatalog(DeviceVariant... variants) {
        when(modelRepository.findWithCatalogById(MODEL_ID)).thenReturn(Optional.of(model()));
        when(variantRepository.findByModelIdAndIsActiveTrueOrderByIdAsc(MODEL_ID))
                .thenReturn(List.of(variants));
    }

    private DeviceModel model() {
        DeviceCategory category = DeviceCategory.create(DeviceType.SMARTPHONE, "일반형 스마트폰", 1);
        ReflectionTestUtils.setField(category, "id", 1L);
        DeviceModel model =
                DeviceModel.create(
                        category, null, "Galaxy S24", "SM-S921N", OsFamily.ANDROID, null, 1);
        ReflectionTestUtils.setField(model, "id", MODEL_ID);
        return model;
    }

    private DeviceVariant variant(String key, String color, Integer storageGb) {
        DeviceModel model = model();
        DeviceVariant variant = DeviceVariant.create(model, key, key);
        variant.withColor(color).withStorage(storageGb);
        ReflectionTestUtils.setField(variant, "id", (long) key.hashCode());
        return variant;
    }

    private Map<DeviceVariantAxis, String> selection(DeviceVariantAxis axis, String value) {
        Map<DeviceVariantAxis, String> selection = new EnumMap<>(DeviceVariantAxis.class);
        selection.put(axis, value);
        return selection;
    }

    private DeviceVariantAxisResponse axis(
            DeviceModelOptionsResponse response, DeviceVariantAxis axis) {
        return response.axes().stream()
                .filter(item -> item.axis().equals(axis.name()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> values(DeviceModelOptionsResponse response, DeviceVariantAxis axis) {
        return axis(response, axis).values();
    }
}
