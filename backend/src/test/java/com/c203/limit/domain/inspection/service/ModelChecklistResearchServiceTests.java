package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.response.ChecklistResearchResponse;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.checklist.ChecklistEvidenceStatus;
import com.c203.limit.domain.inspection.checklist.ChecklistSuggestion;
import com.c203.limit.domain.inspection.checklist.ChecklistSuggestionNormalizer;
import com.c203.limit.domain.inspection.checklist.ChecklistSupplementClient;
import com.c203.limit.domain.inspection.checklist.ChecklistSupplementResult;
import com.c203.limit.domain.inspection.checklist.DeviceChecklistFeatureCatalog;
import com.c203.limit.domain.inspection.checklist.LaptopChecklistPolicy;
import com.c203.limit.domain.inspection.checklist.LaptopFeatureCode;
import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.LatestModelChecklistResearchProjection;
import com.c203.limit.domain.inspection.repository.ModelChecklistResearchRepository;
import com.c203.limit.domain.product.dto.response.ChecklistSuggestionResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ModelChecklistResearchServiceTests {

    private ModelChecklistResearchRepository researchRepository;
    private CategoryRepository categoryRepository;
    private AdminActionLogRepository actionLogRepository;
    private ChecklistSupplementClient supplementClient;
    private PlatformTransactionManager transactionManager;
    private ModelChecklistResearchService service;
    private ObjectMapper objectMapper;
    private AtomicReference<ModelChecklistResearch> savedResearch;

    @BeforeEach
    void setUp() throws Exception {
        researchRepository = mock(ModelChecklistResearchRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        actionLogRepository = mock(AdminActionLogRepository.class);
        supplementClient = mock(ChecklistSupplementClient.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        savedResearch = new AtomicReference<>();
        when(researchRepository.saveAndFlush(any(ModelChecklistResearch.class)))
                .thenAnswer(invocation -> {
                    ModelChecklistResearch research = invocation.getArgument(0);
                    if (research.getId() == null) {
                        ReflectionTestUtils.setField(research, "id", 601L);
                    }
                    savedResearch.set(research);
                    return research;
                });
        when(researchRepository.findByIdForUpdate(601L))
                .thenAnswer(invocation -> Optional.ofNullable(savedResearch.get()));

        objectMapper = new ObjectMapper();
        LaptopChecklistPolicy laptopPolicy = new LaptopChecklistPolicy();
        DeviceChecklistFeatureCatalog featureCatalog = new DeviceChecklistFeatureCatalog();
        service = new ModelChecklistResearchService(
                researchRepository,
                categoryRepository,
                mock(ChecklistTemplateRepository.class),
                mock(ChecklistTemplateItemRepository.class),
                actionLogRepository,
                laptopPolicy,
                featureCatalog,
                new ChecklistSuggestionNormalizer(laptopPolicy, featureCatalog),
                supplementClient,
                objectMapper,
                transactionManager);
    }

    @Test
    void retriesFailedResearchAndMovesItToPendingReview() throws Exception {
        ModelChecklistResearch research = failedResearch();
        when(researchRepository.findById(501L)).thenReturn(Optional.of(research));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(true, List.of(), List.of()));

        ChecklistResearchResponse response = service.retry(501L, 900L);

        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        assertThat(response.failureCode()).isNull();
        assertThat(response.failureMessage()).isNull();
        assertThat(response.researchVersion()).isEqualTo(2);
        assertThat(research.getStatus()).isEqualTo(ModelChecklistResearchStatus.FAILED);
        assertThat(savedResearch.get().getStatus())
                .isEqualTo(ModelChecklistResearchStatus.PENDING_REVIEW);
        verify(actionLogRepository).save(any());
    }

    @Test
    void retryReturnsNormalizedSuggestionFromAiClientPayload() throws Exception {
        ModelChecklistResearch research = failedResearch();
        when(researchRepository.findById(501L)).thenReturn(Optional.of(research));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(
                        true,
                        List.of(new ChecklistSuggestion(
                                " camera ",
                                "내장 카메라",
                                ChecklistEvidenceStatus.VERIFIED,
                                "제조사 공식 자료에서 확인했습니다.",
                                "카메라 앱에서 실제 동작을 확인하세요.",
                                "https://www.samsung.com/support/camera",
                                "Samsung support")),
                        List.of()));

        ChecklistResearchResponse response = service.retry(501L, 900L);

        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        assertThat(response.suggestions())
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.featureCode()).isEqualTo("CAMERA");
                    assertThat(suggestion.evidenceType()).isEqualTo("SELLER_CONFIRMATION");
                    assertThat(suggestion.itemCode()).isEqualTo("LAP-FTR-CAM");
                });
    }

    @Test
    void keepsFailureReasonWhenRetryFailsAgain() throws Exception {
        ModelChecklistResearch research = failedResearch();
        when(researchRepository.findById(501L)).thenReturn(Optional.of(research));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(ChecklistSupplementResult.requestFailed());

        ChecklistResearchResponse response = service.retry(501L, 900L);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureCode()).isEqualTo("AI_REQUEST_FAILED");
        assertThat(response.failureMessage()).contains("시간 초과");
        assertThat(savedResearch.get().getResultJson())
                .contains("\"failureCode\":\"AI_REQUEST_FAILED\"");
    }

    @Test
    void retryThrowsWhenResearchDoesNotExist() {
        when(researchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(999L, 900L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_RESEARCH_NOT_FOUND));
    }

    @Test
    void retryThrowsWhenResearchIsNotInFailedState() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(702L, supplementJson());
        when(researchRepository.findById(702L)).thenReturn(Optional.of(research));

        assertThatThrownBy(() -> service.retry(702L, 900L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_RESEARCH_STATE_CONFLICT));
    }

    @Test
    void researchModelStartsFirstVersionAndFallsBackWhenClientThrows() {
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenThrow(new IllegalStateException("upstream exploded"));

        ChecklistResearchResponse response = service.researchModel(201L, 900L);

        assertThat(response.researchVersion()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureCode()).isEqualTo("AI_REQUEST_FAILED");
        assertThat(savedResearch.get().getInputSnapshotJson())
                .contains("\"deviceModelId\":201")
                .contains("\"deviceType\":\"LAPTOP\"")
                .contains("\"modelCode\":\"NT960\"");
    }

    @Test
    void researchModelThrowsWhenStartedResearchDisappearedBeforeCompletion() {
        when(researchRepository.findByIdForUpdate(601L)).thenReturn(Optional.empty());
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(true, List.of(), List.of()));

        assertThatThrownBy(() -> service.researchModel(201L, 900L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_RESEARCH_NOT_FOUND));
    }

    @Test
    void researchModelThrowsWhenStartedResearchIsNoLongerProcessing() throws Exception {
        when(researchRepository.findByIdForUpdate(601L))
                .thenReturn(Optional.of(pendingReviewResearch(601L, supplementJson())));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(true, List.of(), List.of()));

        assertThatThrownBy(() -> service.researchModel(201L, 900L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_RESEARCH_STATE_CONFLICT));
    }

    @Test
    void researchModelThrowsWhenContextSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(serializationFailure());
        ModelChecklistResearchService failingService = serviceWith(failingMapper);
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        assertThatThrownBy(() -> failingService.researchModel(201L, 900L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to serialize checklist research input");
    }

    @Test
    void researchModelThrowsWhenSupplementSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenReturn("{}")
                .thenThrow(serializationFailure());
        ModelChecklistResearchService failingService = serviceWith(failingMapper);
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(true, List.of(), List.of()));

        assertThatThrownBy(() -> failingService.researchModel(201L, 900L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed to serialize checklist research");
    }

    @Test
    void listWithoutStatusReadsEveryResearch() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                701L,
                supplementJson(
                        suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/a"),
                        suggestion(LaptopFeatureCode.WIFI, "http://www.samsung.com/b")));
        when(researchRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        List<ChecklistResearchResponse> responses = service.list(null);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.researchId()).isEqualTo(701L);
            assertThat(response.deviceModelId()).isEqualTo(201L);
            assertThat(response.deviceType()).isEqualTo("LAPTOP");
            assertThat(response.status()).isEqualTo("PENDING_REVIEW");
            assertThat(response.suggestions())
                    .extracting(ChecklistSuggestionResponse::featureCode)
                    .containsExactly("CAMERA");
            assertThat(response.reviewCandidates()).containsExactly("추가 검토 후보");
        });
    }

    @Test
    void listWithStatusUsesStatusFilteredQuery() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(701L, supplementJson());
        when(researchRepository.findByStatusOrderByCreatedAtAsc(
                        ModelChecklistResearchStatus.PENDING_REVIEW))
                .thenReturn(List.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        List<ChecklistResearchResponse> responses =
                service.list(ModelChecklistResearchStatus.PENDING_REVIEW);

        assertThat(responses).hasSize(1);
        verify(researchRepository)
                .findByStatusOrderByCreatedAtAsc(ModelChecklistResearchStatus.PENDING_REVIEW);
    }

    @Test
    void latestReturnsNullWhenNoResearchExists() {
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.empty());

        assertThat(service.latest(201L)).isNull();
    }

    @Test
    void latestReportsUnavailableSupplementWhenResultIsNotStoredYet() {
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(processingResearch(703L)));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response = service.latest(201L);

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.failureCode()).isEqualTo("AI_UNAVAILABLE");
        assertThat(response.suggestions()).isEmpty();
        assertThat(response.reviewCandidates()).isEmpty();
    }

    @Test
    void latestThrowsWhenDeviceModelIsMissing() {
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(processingResearch(703L)));
        when(categoryRepository.findById(201L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(201L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    @Test
    void latestFailsLoudlyWhenStoredResultIsNotReadable() {
        ModelChecklistResearch research = pendingReviewResearch(704L, "not-json{");
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        assertThatThrownBy(() -> service.latest(201L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to read checklist research");
    }

    @Test
    void latestDropsSuggestionsWithoutUsableFeatureCode() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                705L,
                supplementJson(
                        rawSuggestion(null, "https://www.samsung.com/a"),
                        rawSuggestion("   ", "https://www.samsung.com/b"),
                        rawSuggestion("TELEPORT", "https://www.samsung.com/c"),
                        suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/d")));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response = service.latest(201L);

        assertThat(response.suggestions())
                .extracting(ChecklistSuggestionResponse::featureCode)
                .containsExactly("CAMERA");
    }

    @Test
    void latestDropsSuggestionsWithUnsafeSourceUrls() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                706L,
                supplementJson(
                        suggestion(LaptopFeatureCode.WIFI, null),
                        suggestion(LaptopFeatureCode.OLED, "   "),
                        suggestion(LaptopFeatureCode.NUMPAD, "http://www.samsung.com/a"),
                        suggestion(LaptopFeatureCode.SD_CARD, "https:relative-only"),
                        suggestion(LaptopFeatureCode.THUNDERBOLT, "https://bad host.com/b"),
                        suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/c")));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response = service.latest(201L);

        assertThat(response.suggestions())
                .extracting(ChecklistSuggestionResponse::featureCode)
                .containsExactly("CAMERA");
    }

    @Test
    void latestKeepsAtMostFiveSuggestions() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                707L,
                supplementJson(
                        suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/1"),
                        suggestion(LaptopFeatureCode.WIFI, "https://www.samsung.com/2"),
                        suggestion(LaptopFeatureCode.OLED, "https://www.samsung.com/3"),
                        suggestion(LaptopFeatureCode.NUMPAD, "https://www.samsung.com/4"),
                        suggestion(LaptopFeatureCode.SD_CARD, "https://www.samsung.com/5"),
                        suggestion(LaptopFeatureCode.THUNDERBOLT, "https://www.samsung.com/6")));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response = service.latest(201L);

        assertThat(response.suggestions())
                .hasSize(DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS)
                .extracting(ChecklistSuggestionResponse::featureCode)
                .containsExactly("CAMERA", "WIFI", "OLED", "NUMPAD", "SD_CARD");
    }

    @Test
    void latestDeduplicatesSuggestionsBeforeApplyingTheLimit() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                708L,
                supplementJson(
                        rawSuggestion(" camera ", "https://www.samsung.com/1"),
                        suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/2"),
                        suggestion(LaptopFeatureCode.WIFI, "https://www.samsung.com/3"),
                        suggestion(LaptopFeatureCode.OLED, "https://www.samsung.com/4"),
                        suggestion(LaptopFeatureCode.NUMPAD, "https://www.samsung.com/5"),
                        suggestion(LaptopFeatureCode.SD_CARD, "https://www.samsung.com/6"),
                        suggestion(LaptopFeatureCode.THUNDERBOLT, "https://www.samsung.com/7")));
        when(researchRepository.findFirstByDeviceModelIdOrderByResearchVersionDesc(201L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response = service.latest(201L);

        assertThat(response.suggestions())
                .hasSize(DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS)
                .extracting(ChecklistSuggestionResponse::featureCode)
                .containsExactly("CAMERA", "WIFI", "OLED", "NUMPAD", "SD_CARD");
    }

    @Test
    void latestSummariesReturnsEmptyMapWithoutModelIds() {
        assertThat(service.latestSummaries(null)).isEmpty();
        assertThat(service.latestSummaries(Set.of())).isEmpty();
    }

    @Test
    void latestSummariesIndexesProjectionsByDeviceModelId() {
        LatestModelChecklistResearchProjection projection =
                mock(LatestModelChecklistResearchProjection.class);
        when(projection.getDeviceModelId()).thenReturn(201L);
        when(projection.getStatus()).thenReturn(ModelChecklistResearchStatus.APPROVED);
        when(projection.getResearchVersion()).thenReturn(4);
        when(researchRepository.findLatestSummaries(Set.of(201L)))
                .thenReturn(List.of(projection));

        Map<Long, ModelChecklistResearchService.LatestResearchSummary> summaries =
                service.latestSummaries(Set.of(201L));

        assertThat(summaries).containsOnlyKeys(201L);
        assertThat(summaries.get(201L).status())
                .isEqualTo(ModelChecklistResearchStatus.APPROVED);
        assertThat(summaries.get(201L).researchVersion()).isEqualTo(4);
    }

    @Test
    void modelIdsByLatestStatusReturnsEmptySetWhenStatusIsNull() {
        assertThat(service.modelIdsByLatestStatus(null)).isEmpty();
    }

    @Test
    void modelIdsByLatestStatusPassesStatusNameToNativeQuery() {
        when(researchRepository.findDeviceModelIdsByLatestStatus("PENDING_REVIEW"))
                .thenReturn(List.of(201L, 202L));

        assertThat(service.modelIdsByLatestStatus(ModelChecklistResearchStatus.PENDING_REVIEW))
                .containsExactlyInAnyOrder(201L, 202L);
    }

    @Test
    void historyThrowsWhenDeviceModelIsMissing() {
        when(categoryRepository.findById(201L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(201L, 0, 10))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    @Test
    void historyAppliesDefaultPagingAndDescendingVersionSort() {
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));
        when(researchRepository.findByDeviceModelId(eq(201L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(processingResearch(708L)), PageRequest.of(0, 20), 1));

        PageResponse<ChecklistResearchResponse> response = service.history(201L, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(researchRepository).findByDeviceModelId(eq(201L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "researchVersion"));
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    void historyClampsOutOfRangePageAndSize() {
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));
        when(researchRepository.findByDeviceModelId(eq(201L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.history(201L, -3, 500);
        service.history(201L, 2, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(researchRepository, times(2)).findByDeviceModelId(eq(201L), captor.capture());
        assertThat(captor.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(100);
        assertThat(captor.getAllValues().get(1).getPageNumber()).isEqualTo(2);
        assertThat(captor.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }

    @Test
    void countByModelIdDelegatesToRepository() {
        when(researchRepository.countByDeviceModelId(201L)).thenReturn(7L);

        assertThat(service.countByModelId(201L)).isEqualTo(7L);
    }

    @Test
    void approveNormalizesRequestedFeatureCodesAndRecordsAdminAction() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                709L,
                supplementJson(
                        suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/a"),
                        suggestion(LaptopFeatureCode.WIFI, "https://www.samsung.com/b")));
        when(researchRepository.findByIdForUpdate(709L)).thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response =
                service.approve(709L, 900L, Set.of("  camera  "), "  검토 완료  ");

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewedByAdminId()).isEqualTo(900L);
        assertThat(response.reviewNote()).isEqualTo("검토 완료");
        assertThat(response.publishedTemplateId()).isNull();
        assertThat(research.getStatus()).isEqualTo(ModelChecklistResearchStatus.APPROVED);
        verify(actionLogRepository).save(any());
    }

    @Test
    void approveWithoutRequestedCodesFallsBackToEverySuggestedCode() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                710L,
                supplementJson(suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/a")));
        when(researchRepository.findByIdForUpdate(710L)).thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response = service.approve(710L, 900L, null, null);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewNote()).isNull();
    }

    @Test
    void approveRejectsCodesThatWereNotSuggested() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                711L,
                supplementJson(suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/a")));
        when(researchRepository.findByIdForUpdate(711L)).thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        assertThatThrownBy(() -> service.approve(711L, 900L, Set.of("WIFI"), null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThat(research.getStatus())
                .isEqualTo(ModelChecklistResearchStatus.PENDING_REVIEW);
    }

    @Test
    void approveRejectsBlankOrNullFeatureCodes() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(
                712L,
                supplementJson(suggestion(LaptopFeatureCode.CAMERA, "https://www.samsung.com/a")));
        when(researchRepository.findByIdForUpdate(712L)).thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        assertThatThrownBy(() -> service.approve(712L, 900L, Set.of("CAMERA", "   "), null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.approve(
                        712L, 900L, new HashSet<>(Arrays.asList("CAMERA", null)), null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void approveThrowsWhenResearchDoesNotExist() {
        when(researchRepository.findByIdForUpdate(713L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(713L, 900L, null, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_RESEARCH_NOT_FOUND));
    }

    @Test
    void approveThrowsWhenResearchWasAlreadyReviewed() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(714L, supplementJson());
        research.approve(900L, null, "먼저 승인됨");
        when(researchRepository.findByIdForUpdate(714L)).thenReturn(Optional.of(research));

        assertThatThrownBy(() -> service.approve(714L, 901L, null, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_RESEARCH_STATE_CONFLICT));
    }

    @Test
    void approveThrowsWhenDeviceModelIsInactive() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(715L, supplementJson());
        when(researchRepository.findByIdForUpdate(715L)).thenReturn(Optional.of(research));
        Category inactive = laptop();
        inactive.deactivate();
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.approve(715L, 900L, null, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    @Test
    void rejectMarksResearchRejectedAndRecordsAdminAction() throws Exception {
        ModelChecklistResearch research = pendingReviewResearch(716L, supplementJson());
        when(researchRepository.findByIdForUpdate(716L)).thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(laptop()));

        ChecklistResearchResponse response = service.reject(716L, 900L, "근거 부족");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reviewNote()).isEqualTo("근거 부족");
        assertThat(response.reviewedByAdminId()).isEqualTo(900L);
        assertThat(research.getStatus()).isEqualTo(ModelChecklistResearchStatus.REJECTED);
        verify(actionLogRepository).save(any());
    }

    @Test
    void rejectThrowsWhenResearchIsStillProcessing() {
        when(researchRepository.findByIdForUpdate(717L))
                .thenReturn(Optional.of(processingResearch(717L)));

        assertThatThrownBy(() -> service.reject(717L, 900L, "무시"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_RESEARCH_STATE_CONFLICT));
    }

    private ModelChecklistResearchService serviceWith(ObjectMapper mapper) {
        LaptopChecklistPolicy laptopPolicy = new LaptopChecklistPolicy();
        DeviceChecklistFeatureCatalog featureCatalog = new DeviceChecklistFeatureCatalog();
        return new ModelChecklistResearchService(
                researchRepository,
                categoryRepository,
                mock(ChecklistTemplateRepository.class),
                mock(ChecklistTemplateItemRepository.class),
                actionLogRepository,
                laptopPolicy,
                featureCatalog,
                new ChecklistSuggestionNormalizer(laptopPolicy, featureCatalog),
                supplementClient,
                mapper,
                transactionManager);
    }

    private JsonProcessingException serializationFailure() {
        return new JsonProcessingException("boom") {};
    }

    private String supplementJson(ChecklistSuggestion... suggestions) throws Exception {
        return objectMapper.writeValueAsString(new ChecklistSupplementResult(
                true, List.of(suggestions), List.of("추가 검토 후보")));
    }

    private ChecklistSuggestion suggestion(LaptopFeatureCode code, String sourceUrl) {
        return rawSuggestion(code.name(), sourceUrl);
    }

    private ChecklistSuggestion rawSuggestion(String featureCode, String sourceUrl) {
        return new ChecklistSuggestion(
                featureCode,
                "기능",
                ChecklistEvidenceStatus.VERIFIED,
                "제조사 공식 자료에서 확인했습니다.",
                "실제 동작 여부를 확인하세요.",
                sourceUrl,
                "제조사 지원 페이지");
    }

    private ModelChecklistResearch pendingReviewResearch(long id, String resultJson) {
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 3, "{}");
        research.complete(resultJson);
        ReflectionTestUtils.setField(research, "id", id);
        return research;
    }

    private ModelChecklistResearch processingResearch(long id) {
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1, "{}");
        ReflectionTestUtils.setField(research, "id", id);
        return research;
    }

    private ModelChecklistResearch failedResearch() throws Exception {
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1);
        research.fail(objectMapper.writeValueAsString(
                ChecklistSupplementResult.requestFailed()));
        ReflectionTestUtils.setField(research, "id", 501L);
        return research;
    }

    private Category laptop() {
        Category parent =
                Category.createTopLevel("노트북", DeviceType.LAPTOP, 1);
        Category model = Category.createLeaf(
                parent,
                "Galaxy Book4",
                DeviceType.LAPTOP,
                "Samsung",
                OsFamily.WINDOWS,
                "NT960",
                List.of(512),
                1);
        ReflectionTestUtils.setField(model, "id", 201L);
        return model;
    }
}
