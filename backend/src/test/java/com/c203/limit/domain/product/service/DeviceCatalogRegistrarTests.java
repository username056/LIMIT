package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceCategory;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceVariant;
import com.c203.limit.domain.product.entity.Manufacturer;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.DeviceCategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceVariantRepository;
import com.c203.limit.domain.product.repository.ManufacturerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceCatalogRegistrarTests {

    private static final long PARENT_ID = 1L;
    private static final long LEAF_ID = 42L;

    @Mock DeviceCategoryRepository deviceCategoryRepository;
    @Mock ManufacturerRepository manufacturerRepository;
    @Mock DeviceModelRepository modelRepository;
    @Mock DeviceVariantRepository variantRepository;

    DeviceCatalogRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new DeviceCatalogRegistrar(
                deviceCategoryRepository,
                manufacturerRepository,
                modelRepository,
                variantRepository);
    }

    /**
     * 핵심 계약. 두 id가 어긋나면 그 모델로 등록한 상품이 {@code fk_listing_device_model}
     * 위반으로 실패한다.
     */
    @Test
    void keepsModelIdEqualToLeafCategoryId() {
        givenCatalog();
        when(modelRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        DeviceModel model = registrar.register(leaf());

        assertThat(model.getId()).isEqualTo(LEAF_ID);
        assertThat(model.getModelCode()).isEqualTo("SM-S931N");
        assertThat(model.getModelName()).isEqualTo("Galaxy S25");
    }

    /** 조합이 없는 모델은 검색에서 제외된다. 승인된 모델이 그 규칙에 걸려 사라지면 안 된다. */
    @Test
    void createsBaseVariantSoTheModelStaysSearchable() {
        givenCatalog();
        when(modelRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        registrar.register(leaf());

        ArgumentCaptor<DeviceVariant> captor = ArgumentCaptor.forClass(DeviceVariant.class);
        verify(variantRepository).save(captor.capture());
        assertThat(captor.getValue().getVariantKey()).isEqualTo("SM-S931N-BASE");
    }

    /** 승인마다 새로 만들면 "Samsung"과 "samsung"이 다른 제조사가 되어 필터가 갈라진다. */
    @Test
    void reusesExistingManufacturer() {
        givenCatalog();
        Manufacturer existing = Manufacturer.create("Samsung");
        when(manufacturerRepository.findById(Manufacturer.idOf("Samsung")))
                .thenReturn(Optional.of(existing));
        when(modelRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        DeviceModel model = registrar.register(leaf());

        assertThat(model.manufacturerId()).isEqualTo(Manufacturer.idOf("samsung"));
        verify(manufacturerRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsManufacturerWhenMissing() {
        givenCatalog();
        when(manufacturerRepository.findById(any())).thenReturn(Optional.empty());
        when(manufacturerRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(modelRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        registrar.register(leaf());

        verify(manufacturerRepository).saveAndFlush(any());
    }

    /** CRC32가 충돌해도 서로 다른 제조사를 같은 행으로 조용히 합치면 안 된다. */
    @Test
    void rejectsManufacturerIdCollision() {
        givenCatalog();
        when(manufacturerRepository.findById(Manufacturer.idOf("Samsung")))
                .thenReturn(Optional.of(Manufacturer.create("Apple")));

        assertThatThrownBy(() -> registrar.register(leaf()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manufacturer id collision");
        verify(manufacturerRepository, never()).saveAndFlush(any());
        verify(modelRepository, never()).saveAndFlush(any());
    }

    /** 승인 재시도나 이관 이후 재실행에서 문서가 두 번 생기면 안 된다. */
    @Test
    void doesNothingWhenAlreadyRegistered() {
        DeviceModel already = DeviceModel.create(
                LEAF_ID, deviceCategory(), null, "Galaxy S25", "SM-S931N", OsFamily.ANDROID, null, 1);
        when(modelRepository.findById(LEAF_ID)).thenReturn(Optional.of(already));

        assertThat(registrar.register(leaf())).isSameAs(already);
        verify(modelRepository, never()).saveAndFlush(any());
        verify(variantRepository, never()).save(any());
    }

    /** 최상위 카테고리가 아직 이관되지 않았다면 조용히 넘어가면 안 된다. */
    @Test
    void failsWhenParentCategoryIsNotMigrated() {
        when(modelRepository.findById(LEAF_ID)).thenReturn(Optional.empty());
        when(deviceCategoryRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar.register(leaf()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not migrated");
    }

    @Test
    void rejectsNonLeafCategory() {
        Category top = Category.createTopLevel("일반형 스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(top, "id", PARENT_ID);

        assertThatThrownBy(() -> registrar.register(top))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsavedCategory() {
        assertThatThrownBy(() -> registrar.register(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 사용자가 올린 모델은 검토 대기 상태로 들어가야 관리자가 확인할 대상이 된다. */
    @Test
    void marksUserReportedModelAsPendingReview() {
        givenCatalog();
        when(modelRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        DeviceModel model = registrar.registerReported(leaf(), 7L);

        assertThat(model.getReviewStatus())
                .isEqualTo(com.c203.limit.domain.product.entity.DeviceModelReviewStatus
                        .PENDING_REVIEW);
        assertThat(model.getSourceType())
                .isEqualTo(com.c203.limit.domain.product.entity.DeviceModelSourceType.USER_REPORT);
        assertThat(model.getReportedByMemberId()).isEqualTo(7L);
    }

    @Test
    void leavesManufacturerEmptyWhenLeafHasNoManufacturerName() {
        when(modelRepository.findById(LEAF_ID)).thenReturn(Optional.empty());
        when(deviceCategoryRepository.findById(PARENT_ID))
                .thenReturn(Optional.of(deviceCategory()));
        when(modelRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        Category leaf = leaf();
        ReflectionTestUtils.setField(leaf, "manufacturer", null);

        DeviceModel model = registrar.register(leaf);

        assertThat(model.manufacturerId()).isNull();
        verify(manufacturerRepository, never()).findById(any());
        verify(manufacturerRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsLeafWithoutModelCode() {
        Category parent = Category.createTopLevel("일반형 스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(parent, "id", PARENT_ID);
        Category leaf = Category.createLeaf(
                parent, "Galaxy S25", DeviceType.SMARTPHONE, "Samsung", OsFamily.ANDROID,
                null, List.of(), 3);
        ReflectionTestUtils.setField(leaf, "id", LEAF_ID);

        assertThatThrownBy(() -> registrar.register(leaf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("device model leaf");
        verify(modelRepository, never()).findById(any());
    }

    @Test
    void rejectsLeafThatWasNeverPersisted() {
        Category parent = Category.createTopLevel("일반형 스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(parent, "id", PARENT_ID);
        Category leaf = Category.createLeaf(
                parent, "Galaxy S25", DeviceType.SMARTPHONE, "Samsung", OsFamily.ANDROID,
                "SM-S931N", List.of(), 3);

        assertThatThrownBy(() -> registrar.register(leaf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persisted");
    }

    @Test
    void rewritesCatalogRowWhenLeafMovesToAnotherCategory() {
        Category parent = Category.createTopLevel("노트북", DeviceType.LAPTOP, 2);
        ReflectionTestUtils.setField(parent, "id", 2L);
        Category leaf = Category.createLeaf(
                parent, "Galaxy Book4", DeviceType.LAPTOP, "Samsung", OsFamily.WINDOWS,
                "NT960XGK", List.of(), 3);
        ReflectionTestUtils.setField(leaf, "id", LEAF_ID);
        DeviceCategory movedCategory = DeviceCategory.create(DeviceType.LAPTOP, "노트북", 2);
        ReflectionTestUtils.setField(movedCategory, "id", 2L);
        when(modelRepository.findByIdForUpdate(LEAF_ID)).thenReturn(Optional.of(registered()));
        when(deviceCategoryRepository.findById(2L)).thenReturn(Optional.of(movedCategory));
        when(manufacturerRepository.findById(Manufacturer.idOf("Samsung")))
                .thenReturn(Optional.of(Manufacturer.create("Samsung")));

        DeviceModel model = registrar.update(leaf);

        assertThat(model.getModelName()).isEqualTo("Galaxy Book4");
        assertThat(model.getModelCode()).isEqualTo("NT960XGK");
        assertThat(model.getOsFamily()).isEqualTo(OsFamily.WINDOWS);
        assertThat(model.getCategory()).isSameAs(movedCategory);
    }

    @Test
    void failsUpdateWhenModelWasNeverRegistered() {
        when(modelRepository.findByIdForUpdate(LEAF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar.update(leaf()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not registered");
        verify(deviceCategoryRepository, never()).findById(any());
    }

    @Test
    void failsUpdateWhenTargetCategoryIsNotMigrated() {
        when(modelRepository.findByIdForUpdate(LEAF_ID)).thenReturn(Optional.of(registered()));
        when(deviceCategoryRepository.findById(PARENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar.update(leaf()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not migrated");
        verify(manufacturerRepository, never()).findById(any());
    }

    @Test
    void completeReviewMarksReportedModelVerified() {
        DeviceModel model = DeviceModel.createReported(
                LEAF_ID, deviceCategory(), null, "Galaxy S25", "SM-S931N", OsFamily.ANDROID, 1, 7L);
        when(modelRepository.findByIdForUpdate(LEAF_ID)).thenReturn(Optional.of(model));

        registrar.completeReview(LEAF_ID, 9L, "공식 모델 확인");

        assertThat(model.getReviewStatus())
                .isEqualTo(com.c203.limit.domain.product.entity.DeviceModelReviewStatus.VERIFIED);
        assertThat(model.getReviewedByAdminId()).isEqualTo(9L);
        assertThat(model.getReviewNote()).isEqualTo("공식 모델 확인");
    }

    @Test
    void failsCompleteReviewWhenModelIsNotInTheCatalog() {
        when(modelRepository.findByIdForUpdate(LEAF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar.completeReview(LEAF_ID, 9L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not registered");
    }

    @Test
    void deactivateHidesRegisteredModel() {
        DeviceModel model = registered();
        when(modelRepository.findByIdForUpdate(LEAF_ID)).thenReturn(Optional.of(model));

        registrar.deactivate(LEAF_ID);

        assertThat(model.isActive()).isFalse();
    }

    /** 거절 처리는 카탈로그 행이 없더라도 조용히 끝나야 한다. */
    @Test
    void deactivateIsSilentWhenModelIsNotInTheCatalog() {
        when(modelRepository.findByIdForUpdate(LEAF_ID)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> registrar.deactivate(LEAF_ID));
    }

    private DeviceModel registered() {
        return DeviceModel.create(
                LEAF_ID,
                deviceCategory(),
                Manufacturer.create("Samsung"),
                "Galaxy S25",
                "SM-S931N",
                OsFamily.ANDROID,
                null,
                3);
    }

    private void givenCatalog() {
        when(modelRepository.findById(LEAF_ID)).thenReturn(Optional.empty());
        when(deviceCategoryRepository.findById(PARENT_ID))
                .thenReturn(Optional.of(deviceCategory()));
    }

    private DeviceCategory deviceCategory() {
        DeviceCategory category =
                DeviceCategory.create(DeviceType.SMARTPHONE, "일반형 스마트폰", 1);
        ReflectionTestUtils.setField(category, "id", PARENT_ID);
        return category;
    }

    private Category leaf() {
        Category parent = Category.createTopLevel("일반형 스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(parent, "id", PARENT_ID);
        Category leaf = Category.createLeaf(
                parent,
                "Galaxy S25",
                DeviceType.SMARTPHONE,
                "Samsung",
                OsFamily.ANDROID,
                "SM-S931N",
                List.of(),
                3);
        ReflectionTestUtils.setField(leaf, "id", LEAF_ID);
        return leaf;
    }
}
