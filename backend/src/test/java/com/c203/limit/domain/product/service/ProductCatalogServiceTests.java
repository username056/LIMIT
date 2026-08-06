package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.AccountRemovalGuide;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.AccountRemovalGuideRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelDetailResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTests {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ChecklistTemplateRepository templateRepository;
    @Mock private ChecklistTemplateItemRepository templateItemRepository;
    @Mock private AccountRemovalGuideRepository guideRepository;

    private ProductCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogService(
                categoryRepository, templateRepository, templateItemRepository, guideRepository);
    }

    @Test
    void categoriesReadsActiveRootsWhenParentIsNotGiven() {
        Category root = mock(Category.class);
        when(root.getId()).thenReturn(1L);
        when(root.getDeviceType()).thenReturn(DeviceType.SMARTPHONE);
        when(root.getName()).thenReturn("스마트폰");
        when(root.isActive()).thenReturn(true);
        when(categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(root));

        List<DeviceCategoryResponse> result = service.categories(null, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryId()).isEqualTo(1L);
        assertThat(result.get(0).getCode()).isEqualTo("SMARTPHONE");
        assertThat(result.get(0).getName()).isEqualTo("스마트폰");
        assertThat(result.get(0).getParentId()).isNull();
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    void categoriesIncludesInactiveRootsWhenActiveFilterIsDisabled() {
        Category root = mock(Category.class);
        when(root.getDeviceType()).thenReturn(DeviceType.LAPTOP);
        when(root.isActive()).thenReturn(false);
        when(categoryRepository.findByParentIsNullOrderByDisplayOrderAsc())
                .thenReturn(List.of(root));

        List<DeviceCategoryResponse> result = service.categories(null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isActive()).isFalse();
        verify(categoryRepository).findByParentIsNullOrderByDisplayOrderAsc();
    }

    @Test
    void categoriesExposesParentIdentifierForChildLookup() {
        Category parent = mock(Category.class);
        Category child = mock(Category.class);
        when(parent.getId()).thenReturn(7L);
        when(child.getId()).thenReturn(70L);
        when(child.getDeviceType()).thenReturn(DeviceType.FOLDABLE);
        when(child.getParent()).thenReturn(parent);
        when(categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(7L))
                .thenReturn(List.of(child));

        List<DeviceCategoryResponse> result = service.categories(7L, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryId()).isEqualTo(70L);
        assertThat(result.get(0).getParentId()).isEqualTo(7L);
    }

    @Test
    void categoriesUsesUnfilteredChildLookupWhenActiveFilterIsDisabled() {
        when(categoryRepository.findByParentIdOrderByDisplayOrderAsc(7L)).thenReturn(List.of());

        assertThat(service.categories(7L, false)).isEmpty();

        verify(categoryRepository).findByParentIdOrderByDisplayOrderAsc(7L);
    }

    @Test
    void modelsRejectsNegativePage() {
        assertThatThrownBy(() -> service.models(null, null, null, -1, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void modelsRejectsNonPositiveSize() {
        assertThatThrownBy(() -> service.models(null, null, null, 0, 0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void modelsRejectsSizeAboveTheMaximum() {
        assertThatThrownBy(() -> service.models(null, null, null, 0, 101))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void modelsAcceptsTheMaximumSizeBoundary() {
        when(categoryRepository.findModels(null, null, null, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        ProductCatalogService.ModelPage result = service.models(null, null, null, 0, 100);

        assertThat(result.content()).isEmpty();
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isZero();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void modelsTreatsBlankKeywordAsNoKeywordFilter() {
        when(categoryRepository.findModels(3L, 9L, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ProductCatalogService.ModelPage result = service.models(3L, 9L, "   ", 0, 20);

        assertThat(result.content()).isEmpty();
        verify(categoryRepository).findModels(3L, 9L, null, PageRequest.of(0, 20));
    }

    @Test
    void modelsTrimsKeywordAndMapsSummariesWithPageMetadata() {
        Category parent = mock(Category.class);
        Category withParent = mock(Category.class);
        Category withoutParent = mock(Category.class);
        when(parent.getId()).thenReturn(3L);
        when(withParent.getId()).thenReturn(101L);
        when(withParent.getParent()).thenReturn(parent);
        when(withParent.getOsFamily()).thenReturn(OsFamily.ANDROID);
        when(withParent.getManufacturer()).thenReturn("Samsung");
        when(withParent.isActive()).thenReturn(true);
        when(withoutParent.getId()).thenReturn(102L);
        when(categoryRepository.findModels(null, null, "galaxy", PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(
                        List.of(withParent, withoutParent), PageRequest.of(0, 2), 5));

        ProductCatalogService.ModelPage result =
                service.models(null, null, "  galaxy  ", 0, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).getDeviceModelId()).isEqualTo(101L);
        assertThat(result.content().get(0).getCategoryId()).isEqualTo(3L);
        assertThat(result.content().get(0).getManufacturerName()).isEqualTo("Samsung");
        assertThat(result.content().get(0).getDefaultOs()).isEqualTo("ANDROID");
        assertThat(result.content().get(0).isActive()).isTrue();
        assertThat(result.content().get(1).getCategoryId()).isEqualTo(102L);
        assertThat(result.content().get(1).getDefaultOs()).isNull();
        assertThat(result.content().get(1).isActive()).isFalse();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void modelRejectsUnknownModelId() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.model(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    @Test
    void modelRejectsCategoryThatIsNotALeafModel() {
        Category branch = mock(Category.class);
        when(branch.getModelCode()).thenReturn(null);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> service.model(3L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(templateRepository);
    }

    @Test
    void modelRejectsModelWithoutPublishedChecklistTemplate() {
        Category model = mock(Category.class);
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.model(101L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
    }

    @Test
    void modelReturnsDetailWithParsedStorageAndHandoverAvailability() {
        Category parent = mock(Category.class);
        Category model = mock(Category.class);
        ChecklistTemplate template = mock(ChecklistTemplate.class);
        when(parent.getName()).thenReturn("일반형 스마트폰");
        when(model.getId()).thenReturn(101L);
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(model.getName()).thenReturn("Galaxy S24");
        when(model.getParent()).thenReturn(parent);
        when(model.getManufacturer()).thenReturn("Samsung");
        when(model.getDeviceType()).thenReturn(DeviceType.SMARTPHONE);
        when(model.getOsFamily()).thenReturn(OsFamily.ANDROID);
        when(model.getSupportedStorageGb()).thenReturn("256, 512");
        when(template.getVersion()).thenReturn(3);
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));
        when(guideRepository.findFirstByDeviceTypeAndManufacturerOrderByTemplateVersionDesc(
                        DeviceType.SMARTPHONE, "Samsung"))
                .thenReturn(Optional.of(mock(AccountRemovalGuide.class)));

        DeviceModelDetailResponse result = service.model(101L);

        assertThat(result.getDeviceModelId()).isEqualTo(101L);
        assertThat(result.getCategoryName()).isEqualTo("일반형 스마트폰");
        assertThat(result.getModelName()).isEqualTo("Galaxy S24");
        assertThat(result.getModelCode()).isEqualTo("SM-S921N");
        assertThat(result.getDefaultOs()).isEqualTo("ANDROID");
        assertThat(result.getSupportedStorageGb()).containsExactly(256, 512);
        assertThat(result.getChecklistTemplateVersion()).isEqualTo(3);
        assertThat(result.isHandoverGuideAvailable()).isTrue();
    }

    @Test
    void modelFallsBackToOwnNameAndEmptyStorageWhenCatalogValuesAreMissing() {
        Category model = mock(Category.class);
        ChecklistTemplate template = mock(ChecklistTemplate.class);
        when(model.getModelCode()).thenReturn("MBA-M3");
        when(model.getName()).thenReturn("MacBook Air M3");
        when(model.getParent()).thenReturn(null);
        when(model.getManufacturer()).thenReturn("Apple");
        when(model.getDeviceType()).thenReturn(DeviceType.LAPTOP);
        when(model.getOsFamily()).thenReturn(null);
        when(model.getSupportedStorageGb()).thenReturn("   ");
        when(categoryRepository.findById(102L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        102L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));
        when(guideRepository.findFirstByDeviceTypeAndManufacturerOrderByTemplateVersionDesc(
                        DeviceType.LAPTOP, "Apple"))
                .thenReturn(Optional.empty());

        DeviceModelDetailResponse result = service.model(102L);

        assertThat(result.getCategoryName()).isEqualTo("MacBook Air M3");
        assertThat(result.getDefaultOs()).isNull();
        assertThat(result.getSupportedStorageGb()).isEmpty();
        assertThat(result.isHandoverGuideAvailable()).isFalse();
    }

    @Test
    void modelIgnoresMalformedStorageCatalogValue() {
        Category model = mock(Category.class);
        ChecklistTemplate template = mock(ChecklistTemplate.class);
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(model.getName()).thenReturn("Galaxy S24");
        when(model.getDeviceType()).thenReturn(DeviceType.SMARTPHONE);
        when(model.getSupportedStorageGb()).thenReturn("128,abc");
        when(categoryRepository.findById(103L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        103L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));

        DeviceModelDetailResponse result = service.model(103L);

        assertThat(result.getSupportedStorageGb()).isEmpty();
    }

    @Test
    void checklistTemplateRejectsUnknownModelId() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checklistTemplate(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(templateItemRepository);
    }

    @Test
    void checklistTemplateRejectsModelWithoutPublishedTemplate() {
        Category model = mock(Category.class);
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checklistTemplate(101L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));

        verifyNoInteractions(templateItemRepository);
    }

    @Test
    void checklistTemplateMapsPublishedItemsInStoredOrder() {
        Category model = mock(Category.class);
        ChecklistTemplate template = mock(ChecklistTemplate.class);
        ChecklistTemplateItem photoItem = mock(ChecklistTemplateItem.class);
        ChecklistTemplateItem videoItem = mock(ChecklistTemplateItem.class);
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(template.getId()).thenReturn(501L);
        when(template.getVersion()).thenReturn(2);
        when(photoItem.getItemCode()).thenReturn("SP-DSP-001");
        when(photoItem.getEvidenceType()).thenReturn(EvidenceType.PHOTO);
        when(photoItem.isRequired()).thenReturn(true);
        when(photoItem.getMinCount()).thenReturn(1);
        when(photoItem.getMaxCount()).thenReturn(5);
        when(photoItem.isVisibleToBuyer()).thenReturn(true);
        when(videoItem.getItemCode()).thenReturn("SP-DSP-002");
        when(videoItem.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(videoItem.getMinDurationSec()).thenReturn(15);
        when(videoItem.getMaxDurationSec()).thenReturn(60);
        when(videoItem.isPrivacyMaskingRequired()).thenReturn(true);
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));
        when(templateItemRepository.findByChecklistTemplateIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(photoItem, videoItem));

        ChecklistTemplateResponse result = service.checklistTemplate(101L);

        assertThat(result.getTemplateId()).isEqualTo(501L);
        assertThat(result.getDeviceModelId()).isEqualTo(101L);
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getItemCode()).isEqualTo("SP-DSP-001");
        assertThat(result.getItems().get(0).getEvidenceType()).isEqualTo("PHOTO");
        assertThat(result.getItems().get(0).isRequired()).isTrue();
        assertThat(result.getItems().get(0).getMinCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getMaxCount()).isEqualTo(5);
        assertThat(result.getItems().get(0).isRecaptureAllowed()).isTrue();
        assertThat(result.getItems().get(0).isVisibleToBuyer()).isTrue();
        assertThat(result.getItems().get(0).isPrivacyMaskingRequired()).isFalse();
        assertThat(result.getItems().get(1).getItemCode()).isEqualTo("SP-DSP-002");
        assertThat(result.getItems().get(1).getEvidenceType()).isEqualTo("VIDEO");
        assertThat(result.getItems().get(1).isRequired()).isFalse();
        assertThat(result.getItems().get(1).getMinDurationSeconds()).isEqualTo(15);
        assertThat(result.getItems().get(1).getMaxDurationSeconds()).isEqualTo(60);
        assertThat(result.getItems().get(1).isPrivacyMaskingRequired()).isTrue();
    }

    @Test
    void handoverGuideRejectsUnknownModelId() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handoverGuide(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(guideRepository);
    }

    @Test
    void handoverGuideRejectsModelWithoutRegisteredGuide() {
        Category model = mock(Category.class);
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(model.getDeviceType()).thenReturn(DeviceType.SMARTPHONE);
        when(model.getManufacturer()).thenReturn("Samsung");
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(guideRepository.findFirstByDeviceTypeAndManufacturerOrderByTemplateVersionDesc(
                        DeviceType.SMARTPHONE, "Samsung"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handoverGuide(101L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
    }

    @Test
    void handoverGuideNumbersStepsSequentiallyAndDerivesTitle() {
        Category model = mock(Category.class);
        AccountRemovalGuide guide = mock(AccountRemovalGuide.class);
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(model.getName()).thenReturn("Galaxy S24");
        when(model.getDeviceType()).thenReturn(DeviceType.SMARTPHONE);
        when(model.getManufacturer()).thenReturn("Samsung");
        when(guide.getId()).thenReturn(601L);
        when(guide.getTemplateVersion()).thenReturn(4);
        when(guide.getSteps()).thenReturn(List.of("데이터 백업", "계정 로그아웃", "초기화"));
        when(guide.getDisclaimerText()).thenReturn("완전 삭제를 보증하지 않습니다.");
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(guideRepository.findFirstByDeviceTypeAndManufacturerOrderByTemplateVersionDesc(
                        DeviceType.SMARTPHONE, "Samsung"))
                .thenReturn(Optional.of(guide));

        HandoverGuideResponse result = service.handoverGuide(101L);

        assertThat(result.getGuideId()).isEqualTo(601L);
        assertThat(result.getDeviceModelId()).isEqualTo(101L);
        assertThat(result.getVersion()).isEqualTo(4);
        assertThat(result.getTitle()).isEqualTo("Galaxy S24 판매 준비");
        assertThat(result.getDisclaimer()).isEqualTo("완전 삭제를 보증하지 않습니다.");
        assertThat(result.getSteps()).hasSize(3);
        assertThat(result.getSteps().get(0).getOrder()).isEqualTo(1);
        assertThat(result.getSteps().get(0).getCode()).isEqualTo("STEP_1");
        assertThat(result.getSteps().get(0).getTitle()).isEqualTo("데이터 백업");
        assertThat(result.getSteps().get(0).getDescription()).isEqualTo("데이터 백업");
        assertThat(result.getSteps().get(0).isRequired()).isTrue();
        assertThat(result.getSteps().get(2).getOrder()).isEqualTo(3);
        assertThat(result.getSteps().get(2).getCode()).isEqualTo("STEP_3");
    }
}
