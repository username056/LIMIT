package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.ModelChecklistResearchRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChecklistGenerationServiceTests {
    private CategoryRepository categoryRepository;
    private ChecklistTemplateRepository templateRepository;
    private ChecklistTemplateItemRepository templateItemRepository;
    private ModelChecklistResearchRepository researchRepository;
    private ChecklistSupplementClient supplementClient;
    private ChecklistGenerationService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        templateRepository = mock(ChecklistTemplateRepository.class);
        templateItemRepository = mock(ChecklistTemplateItemRepository.class);
        researchRepository = mock(ModelChecklistResearchRepository.class);
        supplementClient = mock(ChecklistSupplementClient.class);
        when(researchRepository.saveAndFlush(any(ModelChecklistResearch.class)))
                .thenAnswer(invocation -> {
                    ModelChecklistResearch research = invocation.getArgument(0);
                    ReflectionTestUtils.setField(research, "id", 401L);
                    return research;
                });
        service = new ChecklistGenerationService(
                categoryRepository,
                templateRepository,
                templateItemRepository,
                researchRepository,
                new LaptopChecklistPolicy(),
                new DeviceChecklistFeatureCatalog(),
                supplementClient,
                new ObjectMapper());
    }

    @Test
    void returnsOfficialAiSuggestionsAndDecoratesConfirmedLaptopFeature() {
        Category model = laptop(OsFamily.WINDOWS);
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(model));
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(
                        true,
                        List.of(new ChecklistSuggestion(
                                "CAMERA",
                                "내장 카메라",
                                ChecklistEvidenceStatus.VERIFIED,
                                "official specification",
                                "카메라 앱에서 영상을 확인하세요.",
                                "https://www.samsung.com/sec/support/model/NT960/",
                                "Samsung support")),
                        List.of("RJ45_PORT", "MICROSD_SLOT", "조도 센서")));

        GeneratedChecklist result =
                service.generateForModel(201L, Set.of("CAMERA"));

        assertThat(result.aiApplied()).isFalse();
        assertThat(result.items()).hasSize(13);
        assertThat(result.aiSuggestions()).isEmpty();
        assertThat(result.reviewCandidates()).isEmpty();
        assertThat(result.researchStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void fallsBackToVerifiedLaptopBaseWhenAiFails() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        when(supplementClient.suggest(any()))
                .thenThrow(new IllegalStateException("temporary failure"));

        GeneratedChecklist result = service.generateForModel(201L, Set.of());

        assertThat(result.aiApplied()).isFalse();
        assertThat(result.items()).hasSize(12);
        assertThat(result.aiSuggestions()).isEmpty();
        assertThat(result.researchStatus()).isEqualTo("FAILED");
    }

    @Test
    void reusesStoredResearchWithoutCallingAiAgain() throws Exception {
        Category model = laptop(OsFamily.WINDOWS);
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1);
        research.complete(new ObjectMapper().writeValueAsString(
                new ChecklistSupplementResult(true, List.of(), List.of())));
        ReflectionTestUtils.setField(research, "id", 401L);
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(model));
        when(researchRepository.findByCategoryIdAndResearchVersion(201L, 1))
                .thenReturn(Optional.of(research));

        GeneratedChecklist result = service.generateForModel(201L, Set.of());

        assertThat(result.researchId()).isEqualTo(401L);
        assertThat(result.researchStatus()).isEqualTo("PENDING_REVIEW");
        verifyNoInteractions(supplementClient);
    }

    @Test
    void rejectsSellerSelectedLaptopFeatureToKeepSharedTemplate() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));

        assertThatThrownBy(() ->
                        service.generateSnapshotForModel(201L, Set.of("CAMERA")))
                .isInstanceOf(com.c203.limit.global.exception.BusinessException.class);
        verifyNoInteractions(supplementClient);
    }

    @Test
    void researchesSmartphoneModelFromPublishedTemplate() {
        Category model = smartphone();
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        stubPublishedTemplate(model.getId());
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(
                        true,
                        List.of(new ChecklistSuggestion(
                                "WIRELESS_CHARGING",
                                "무선 충전",
                                ChecklistEvidenceStatus.VERIFIED,
                                "공식 사양에서 무선 충전을 확인했습니다.",
                                "호환 충전기로 충전 상태를 확인하세요.",
                                "https://www.samsung.com/sec/smartphones/galaxy-s24/",
                                "Galaxy S24")),
                        List.of()));

        GeneratedChecklist result = service.generateForModel(101L, Set.of());

        assertThat(result.aiApplied()).isFalse();
        assertThat(result.items()).extracting(GeneratedChecklistItem::itemCode)
                .containsExactly("EXT-001");
        assertThat(result.aiSuggestions()).isEmpty();
        assertThat(result.researchStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void rejectsSellerSelectedSmartphoneFeatureToKeepSharedTemplate() {
        Category model = smartphone();
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> service.generateSnapshotForModel(
                        101L, Set.of("WIRELESS_CHARGING")))
                .isInstanceOf(com.c203.limit.global.exception.BusinessException.class);
        verifyNoInteractions(supplementClient);
    }

    @Test
    void keepsSharedTemplateWhenOtherDeviceHasNoConfirmedFeature() {
        Category model = smartphone();
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));

        assertThat(service.generateSnapshotForModel(101L, Set.of())).isEmpty();

        verifyNoInteractions(templateRepository, templateItemRepository, supplementClient);
    }

    @Test
    void supportsGenericLinuxChecklistForDirectInput() {
        when(supplementClient.suggest(any()))
                .thenReturn(ChecklistSupplementResult.unavailable());

        GeneratedChecklist result = service.generateCustom(
                "Unknown", "Developer Laptop", null, OsFamily.LINUX, Set.of());

        assertThat(result.deviceModelId()).isNull();
        assertThat(result.osFamily()).isEqualTo(OsFamily.LINUX);
        assertThat(result.items()).hasSize(12);
    }

    // 등록 경로는 생성 결과와 매물의 모델이 같은지 검증하므로, 직접 입력이라도 매물이 매달릴
    // '기타 (직접 입력)' 모델 ID가 결과에 찍혀야 한다.
    @Test
    void stampsCarrierModelIdOnDirectInputChecklistForRegistration() {
        Category carrier = laptop(OsFamily.WINDOWS);
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(carrier));
        when(supplementClient.suggest(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ChecklistSupplementResult.unavailable());

        GeneratedChecklist result = service.generateCustomForModel(
                201L, "LG", "gram Pro 17", "17Z90SP", OsFamily.WINDOWS, Set.of());

        assertThat(result.deviceModelId()).isEqualTo(201L);
        assertThat(result.manufacturer()).isEqualTo("LG");
        assertThat(result.modelName()).isEqualTo("gram Pro 17");
        assertThat(result.items()).isNotEmpty();
    }

    @Test
    void rejectsDirectInputWithoutManufacturerOrModelName() {
        Category carrier = laptop(OsFamily.WINDOWS);
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(carrier));

        assertThatThrownBy(() -> service.generateCustomForModel(
                        201L, "LG", "  ", null, OsFamily.WINDOWS, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(supplementClient);
    }

    @Test
    void rejectsDirectInputOnNonLaptopCarrier() {
        Category phone = Category.createTopLevel("Smartphone", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(phone, "id", 101L);
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(phone));

        assertThatThrownBy(() -> service.generateCustomForModel(
                        101L, "Apple", "iPhone 99", null, OsFamily.WINDOWS, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_DEVICE_TYPE_NOT_SUPPORTED));
    }

    private void stubPublishedTemplate(Long modelId) {
        ChecklistTemplate template = ChecklistTemplate.createDraft(modelId, 1);
        ReflectionTestUtils.setField(template, "id", 301L);
        template.publish();
        ChecklistTemplateItem item = ChecklistTemplateItem.create(
                template,
                "EXT-001",
                "외관 상태",
                "외관 손상을 확인합니다.",
                "기기 외관을 촬영하세요.",
                EvidenceType.PHOTO,
                AutomationType.NONE,
                true,
                1);
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        modelId, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));
        when(templateItemRepository.findByChecklistTemplateIdOrderByDisplayOrderAsc(301L))
                .thenReturn(List.of(item));
    }

    private Category laptop(OsFamily osFamily) {
        Category parent = Category.createTopLevel("Laptop", DeviceType.LAPTOP, 1);
        ReflectionTestUtils.setField(parent, "id", 2L);
        Category model = Category.createLeaf(
                parent,
                "Galaxy Book4 Pro",
                DeviceType.LAPTOP,
                "Samsung",
                osFamily,
                "NT960",
                List.of(512, 1024),
                1);
        ReflectionTestUtils.setField(model, "id", 201L);
        return model;
    }

    private Category smartphone() {
        Category parent =
                Category.createTopLevel("Smartphone", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(parent, "id", 1L);
        Category model = Category.createLeaf(
                parent,
                "Galaxy S24",
                DeviceType.SMARTPHONE,
                "Samsung",
                OsFamily.ANDROID,
                "SM-S921N",
                List.of(256, 512),
                1);
        ReflectionTestUtils.setField(model, "id", 101L);
        return model;
    }
}
