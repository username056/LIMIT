package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
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
        assertThat(result.aiSuggestions()).extracting(ChecklistSuggestion::featureCode)
                .containsExactly("CAMERA");
        assertThat(result.reviewCandidates()).containsExactly("조도 센서");
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
        ArgumentCaptor<ModelChecklistResearch> researchCaptor =
                ArgumentCaptor.forClass(ModelChecklistResearch.class);
        verify(researchRepository, times(2)).saveAndFlush(researchCaptor.capture());
        assertThat(researchCaptor.getValue().getResultJson())
                .contains("\"failureCode\":\"AI_REQUEST_FAILED\"");
    }

    @Test
    void reusesStoredResearchWithoutCallingAiAgain() throws Exception {
        Category model = laptop(OsFamily.WINDOWS);
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1);
        research.complete(new ObjectMapper().writeValueAsString(
                new ChecklistSupplementResult(true, List.of(), List.of())));
        ReflectionTestUtils.setField(research, "id", 401L);
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(model));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));

        GeneratedChecklist result = service.generateForModel(201L, Set.of());

        assertThat(result.researchId()).isEqualTo(401L);
        assertThat(result.researchStatus()).isEqualTo("PENDING_REVIEW");
        verifyNoInteractions(supplementClient);
    }

    @Test
    void createsSellerSnapshotFromSelectedLaptopAiFeature() throws Exception {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        stubPublishedTemplate(201L);
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1);
        // 기존 조사 JSON에는 evidenceType이 없으므로 배포 후에도 서버 정책으로 보강돼야 한다.
        research.complete("""
                {
                  "available": true,
                  "suggestions": [{
                    "featureCode": "CAMERA",
                    "featureName": "내장 카메라",
                    "evidenceStatus": "VERIFIED",
                    "reason": "공식 사양",
                    "checkGuide": "카메라 앱에서 확인하세요.",
                    "sourceUrl": "https://www.samsung.com/sec/support/model/NT960/",
                    "sourceTitle": "Samsung support"
                  }],
                  "reviewCandidates": []
                }
                """);
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));

        GeneratedChecklist result = service
                .generateSnapshotForModel(201L, Set.of("CAMERA"))
                .orElseThrow();

        assertThat(result.aiApplied()).isTrue();
        assertThat(result.items())
                .extracting(GeneratedChecklistItem::itemCode)
                .containsExactly("EXT-001", "LAP-FTR-CAM");
        assertThat(result.items())
                .extracting(GeneratedChecklistItem::featureCode)
                .containsExactly(null, "CAMERA");
        assertThat(result.aiSuggestions())
                .singleElement()
                .extracting(ChecklistSuggestion::evidenceType)
                .isEqualTo(EvidenceType.SELLER_CONFIRMATION);
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
        assertThat(result.aiSuggestions()).extracting(ChecklistSuggestion::featureCode)
                .containsExactly("WIRELESS_CHARGING");
        assertThat(result.researchStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void rejectsSmartphoneFeatureThatWasNotSuggestedByAi() throws Exception {
        Category model = smartphone();
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        ModelChecklistResearch research = ModelChecklistResearch.start(101L, 1);
        research.complete(new ObjectMapper().writeValueAsString(
                new ChecklistSupplementResult(true, List.of(), List.of())));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(101L))
                .thenReturn(Optional.of(research));

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
        assertThat(result.items()).hasSize(11);
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

    @Test
    void resolveConfirmedFeatureItemsReturnsEmptyWhenNothingConfirmed() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));

        assertThat(service.resolveConfirmedFeatureItems(201L, Set.of())).isEmpty();
        assertThat(service.resolveConfirmedFeatureItems(201L, null)).isEmpty();
        verifyNoInteractions(supplementClient, templateRepository);
    }

    @Test
    void resolveConfirmedFeatureItemsNormalizesCodeAndMapsCatalogItem() {
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(smartphone()));

        var items = service.resolveConfirmedFeatureItems(101L, Set.of(" wireless_charging "));

        assertThat(items).containsOnlyKeys("WIRELESS_CHARGING");
        assertThat(items.get("WIRELESS_CHARGING").itemCode()).isEqualTo("PHN-FTR-WCHG");
        assertThat(items.get("WIRELESS_CHARGING").evidenceType()).isEqualTo(EvidenceType.VIDEO);
        verifyNoInteractions(supplementClient);
    }

    @Test
    void resolveConfirmedFeatureItemsRejectsBlankOrUnknownFeatureCode() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));

        assertThatThrownBy(() -> service.resolveConfirmedFeatureItems(201L, Set.of("  ")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.resolveConfirmedFeatureItems(201L, Set.of("HOLOGRAM")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void resolveConfirmedFeatureItemsRejectsMoreFeaturesThanTheCatalogLimit() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));

        assertThatThrownBy(() -> service.resolveConfirmedFeatureItems(
                        201L,
                        Set.of("CAMERA", "WIFI", "BLUETOOTH", "OLED", "NUMPAD", "SD_CARD")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void rejectsDeactivatedDeviceModel() {
        Category model = laptop(OsFamily.WINDOWS);
        model.deactivate();
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> service.generateForModel(201L, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        verifyNoInteractions(supplementClient);
    }

    @Test
    void rejectsUnknownDeviceModel() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateForModel(999L, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    @Test
    void rejectsGenerationWhenNonLaptopModelHasNoPublishedTemplate() throws Exception {
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(smartphone()));
        ModelChecklistResearch research = ModelChecklistResearch.start(101L, 1);
        research.complete(new ObjectMapper().writeValueAsString(
                new ChecklistSupplementResult(true, List.of(), List.of())));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(101L))
                .thenReturn(Optional.of(research));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateForModel(101L, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
    }

    @Test
    void rejectsGenerationWithMoreConfirmedFeaturesThanAllowed() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));

        assertThatThrownBy(() -> service.generateForModel(
                        201L,
                        Set.of("CAMERA", "WIFI", "BLUETOOTH", "OLED", "NUMPAD", "SD_CARD")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(supplementClient);
    }

    @Test
    void rejectsDirectInputOnUnsupportedLaptopOs() {
        assertThatThrownBy(() -> service.generateCustom(
                        "Apple", "MacBook Pro", null, OsFamily.MACOS, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_OS_NOT_SUPPORTED));
        verifyNoInteractions(supplementClient);
    }

    @Test
    void rejectsDirectInputWithoutOsFamily() {
        assertThatThrownBy(() -> service.generateCustom(
                        "LG", "gram Pro 17", "17Z90SP", null, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(supplementClient);
    }

    @Test
    void appliesAiEvidenceToMatchingDirectInputItem() {
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(
                        true,
                        List.of(new ChecklistSuggestion(
                                "CAMERA",
                                "내장 카메라",
                                ChecklistEvidenceStatus.VERIFIED,
                                "공식 사양",
                                "카메라 앱에서 확인하세요.",
                                "https://www.lg.com/support/gram",
                                "LG support")),
                        List.of()));

        GeneratedChecklist result = service.generateCustom(
                "LG", "gram Pro 17", " ", OsFamily.WINDOWS, Set.of("CAMERA"));

        assertThat(result.aiApplied()).isTrue();
        assertThat(result.items())
                .filteredOn(item -> "LAP-FTR-CAM".equals(item.itemCode()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.evidenceStatus()).isEqualTo(ChecklistEvidenceStatus.VERIFIED);
                    assertThat(item.sourceUrl()).isEqualTo("https://www.lg.com/support/gram");
                    assertThat(item.sourceTitle()).isEqualTo("LG support");
                });
        assertThat(result.aiSuggestions())
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.itemCode()).isEqualTo("LAP-FTR-CAM");
                    assertThat(suggestion.evidenceType())
                            .isEqualTo(EvidenceType.SELLER_CONFIRMATION);
                });
    }

    @Test
    void dropsSuggestionsWithUnsafeSourceOrUnknownFeature() {
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(
                        true,
                        List.of(
                                new ChecklistSuggestion(
                                        "WIFI",
                                        "Wi-Fi",
                                        ChecklistEvidenceStatus.VERIFIED,
                                        "공식 사양",
                                        "확인하세요.",
                                        "http://insecure.example.com/spec",
                                        "insecure"),
                                new ChecklistSuggestion(
                                        "  ",
                                        "빈 코드",
                                        ChecklistEvidenceStatus.VERIFIED,
                                        "공식 사양",
                                        "확인하세요.",
                                        "https://www.lg.com/support/gram",
                                        "LG support"),
                                new ChecklistSuggestion(
                                        "HOLOGRAM",
                                        "홀로그램",
                                        ChecklistEvidenceStatus.VERIFIED,
                                        "공식 사양",
                                        "확인하세요.",
                                        "https://www.lg.com/support/gram",
                                        "LG support")),
                        List.of("RJ45_PORT", "   ", "홀로그램 디스플레이")));

        GeneratedChecklist result = service.generateCustom(
                "LG", "gram Pro 17", null, OsFamily.WINDOWS, Set.of());

        assertThat(result.aiSuggestions()).isEmpty();
        assertThat(result.reviewCandidates()).containsExactly("홀로그램 디스플레이");
    }

    @Test
    void keepsVerifiedBaseChecklistWhenDirectInputSupplementFails() {
        when(supplementClient.suggest(any()))
                .thenThrow(new IllegalStateException("timeout"));

        GeneratedChecklist result = service.generateCustom(
                "LG", "gram Pro 17", null, OsFamily.WINDOWS, Set.of());

        assertThat(result.aiApplied()).isFalse();
        assertThat(result.aiSuggestions()).isEmpty();
        assertThat(result.reviewCandidates()).isEmpty();
        assertThat(result.items()).hasSize(12);
    }

    @Test
    void reusesResearchCreatedConcurrentlyWhenInsertViolatesUniqueConstraint() throws Exception {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        ModelChecklistResearch concurrent = ModelChecklistResearch.start(201L, 1);
        concurrent.complete(new ObjectMapper().writeValueAsString(
                new ChecklistSupplementResult(true, List.of(), List.of())));
        ReflectionTestUtils.setField(concurrent, "id", 402L);
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty(), Optional.of(concurrent));
        when(researchRepository.saveAndFlush(any(ModelChecklistResearch.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate research"));

        GeneratedChecklist result = service.generateForModel(201L, Set.of());

        assertThat(result.researchId()).isEqualTo(402L);
        assertThat(result.researchStatus()).isEqualTo("PENDING_REVIEW");
        verifyNoInteractions(supplementClient);
    }

    @Test
    void rethrowsWhenConcurrentResearchIsStillMissingAfterConflict() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty());
        when(researchRepository.saveAndFlush(any(ModelChecklistResearch.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate research"));

        assertThatThrownBy(() -> service.generateForModel(201L, Set.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(supplementClient);
    }

    @Test
    void ignoresResearchResultThatIsNotReviewable() throws Exception {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1);
        research.fail(new ObjectMapper()
                .writeValueAsString(ChecklistSupplementResult.requestFailed()));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));

        GeneratedChecklist result = service.generateForModel(201L, Set.of());

        assertThat(result.researchStatus()).isEqualTo("FAILED");
        assertThat(result.aiApplied()).isFalse();
        assertThat(result.aiSuggestions()).isEmpty();
        assertThat(result.reviewCandidates()).isEmpty();
        verifyNoInteractions(supplementClient);
    }

    @Test
    void failsWhenStoredResearchResultCannotBeDeserialized() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1);
        research.complete("not-a-json-document");
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));

        assertThatThrownBy(() -> service.generateForModel(201L, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to deserialize checklist research");
    }

    @Test
    void marksResearchFailedWhenSupplementIsUnavailable() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        when(supplementClient.suggest(any()))
                .thenReturn(ChecklistSupplementResult.unavailable());

        GeneratedChecklist result = service.generateForModel(201L, Set.of());

        assertThat(result.researchStatus()).isEqualTo("FAILED");
        assertThat(result.researchId()).isEqualTo(401L);
        ArgumentCaptor<ModelChecklistResearch> captor =
                ArgumentCaptor.forClass(ModelChecklistResearch.class);
        verify(researchRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getResultJson())
                .contains("\"failureCode\":\"AI_UNAVAILABLE\"");
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
