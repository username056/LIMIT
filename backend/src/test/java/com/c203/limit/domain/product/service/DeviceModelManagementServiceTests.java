package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelRequest;
import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelStatusRequest;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelDetailResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelImpactResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelSummaryResponse;
import com.c203.limit.domain.admin.dto.response.AdminProductMaterialsResponse;
import com.c203.limit.domain.admin.dto.response.AdminRelatedProductResponse;
import com.c203.limit.domain.admin.dto.response.ChecklistResearchResponse;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.service.ModelChecklistResearchService;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceCategory;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceModelRequest;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.entity.DeviceModelReviewStatus;
import com.c203.limit.domain.product.entity.DeviceModelSourceType;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImage;
import com.c203.limit.domain.product.entity.ListingImageType;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.Manufacturer;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelListingCountProjection;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceModelRequestRepository;
import com.c203.limit.domain.product.repository.DeviceVariantRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusCountProjection;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DeviceModelManagementServiceTests {

    @Mock private DeviceModelRepository modelRepository;
    @Mock private DeviceModelRequestRepository requestRepository;
    @Mock private DeviceModelRequestService requestService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChecklistTemplateRepository templateRepository;
    @Mock private ChecklistTemplateItemRepository templateItemRepository;
    @Mock private DeviceCatalogRegistrar catalogRegistrar;
    @Mock private ModelChecklistResearchService researchService;
    @Mock private AdminActionLogRepository actionLogRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private DeviceVariantRepository variantRepository;
    @Mock private ListingImageRepository imageRepository;
    @Mock private ListingChecklistItemRepository listingChecklistItemRepository;
    @Mock private EvidenceRepository evidenceRepository;
    @Mock private MediaUrlResolver mediaUrlResolver;

    private DeviceModelManagementService service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new DeviceModelManagementService(
                modelRepository,
                requestRepository,
                requestService,
                categoryRepository,
                templateRepository,
                templateItemRepository,
                catalogRegistrar,
                researchService,
                actionLogRepository,
                listingRepository,
                variantRepository,
                imageRepository,
                listingChecklistItemRepository,
                evidenceRepository,
                mediaUrlResolver));
    }

    @Test
    void deactivationPreservesExistingListingsAndStoresAuditReason() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        Category legacyModel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(legacyModel));
        when(legacyModel.getParent()).thenReturn(org.mockito.Mockito.mock(Category.class));
        doReturn(null).when(service).detail(202L);

        service.updateStatus(
                202L,
                9L,
                new UpdateDeviceModelStatusRequest(false, "  중복 등록 모델  ", null));

        verify(legacyModel).deactivate();
        verify(model).deactivate(9L, "중복 등록 모델", null);
        verify(actionLogRepository).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(listingRepository);
    }

    @Test
    void deactivationRejectsTheSameModelAsReplacement() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        Category legacyModel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(legacyModel));
        when(legacyModel.getParent()).thenReturn(org.mockito.Mockito.mock(Category.class));

        assertThatThrownBy(() -> service.updateStatus(
                        202L,
                        9L,
                        new UpdateDeviceModelStatusRequest(false, "중복", 202L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(model, never()).deactivate(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(listingRepository);
    }

    @Test
    void activationRestoresBothCatalogRowsAndStoresAuditReason() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        Category legacyModel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(legacyModel));
        when(legacyModel.getParent()).thenReturn(org.mockito.Mockito.mock(Category.class));
        doReturn(null).when(service).detail(202L);

        service.updateStatus(
                202L, 9L, new UpdateDeviceModelStatusRequest(true, "오등록 해제", null));

        verify(legacyModel).activate();
        verify(model).activate(9L, "오등록 해제");
        verify(actionLogRepository).save(org.mockito.ArgumentMatchers.any());
        verify(model, never()).deactivate(
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any());
    }

    @Test
    void deactivationRejectsAnInactiveReplacementModel() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        DeviceModel replacement = org.mockito.Mockito.mock(DeviceModel.class);
        Category legacyModel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(legacyModel));
        when(legacyModel.getParent()).thenReturn(org.mockito.Mockito.mock(Category.class));
        when(modelRepository.findWithCatalogById(303L)).thenReturn(Optional.of(replacement));
        when(replacement.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.updateStatus(
                        202L, 9L, new UpdateDeviceModelStatusRequest(false, "중복", 303L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(legacyModel, never()).deactivate();
        verifyNoInteractions(actionLogRepository);
    }

    @Test
    void deactivationRejectsABlankReason() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        Category legacyModel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(legacyModel));
        when(legacyModel.getParent()).thenReturn(org.mockito.Mockito.mock(Category.class));

        assertThatThrownBy(() -> service.updateStatus(
                        202L, 9L, new UpdateDeviceModelStatusRequest(false, "   ", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoInteractions(actionLogRepository);
    }

    @Test
    void updateStatusRejectsAModelThatIsMissingFromTheDeviceModelTable() {
        when(modelRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(
                        404L, 9L, new UpdateDeviceModelStatusRequest(false, "중복", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void updateStatusRejectsAModelIdThatIsNotALegacyLeafCategory() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        Category topLevel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(topLevel));
        when(topLevel.getParent()).thenReturn(null);

        assertThatThrownBy(() -> service.updateStatus(
                        202L, 9L, new UpdateDeviceModelStatusRequest(false, "중복", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(actionLogRepository);
    }

    private static final Sort DEFAULT_MODEL_SORT =
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    @Test
    void listShortCircuitsWithNormalizedPagingWhenResearchFilterMatchesNoModel() {
        when(researchService.modelIdsByLatestStatus(ModelChecklistResearchStatus.FAILED))
                .thenReturn(Set.of());

        PageResponse<AdminDeviceModelSummaryResponse> clamped = service.list(
                null, null, null, null, null, ModelChecklistResearchStatus.FAILED, 3, 500, null);
        PageResponse<AdminDeviceModelSummaryResponse> defaulted = service.list(
                null, null, null, null, null, ModelChecklistResearchStatus.FAILED, -5, null, null);
        PageResponse<AdminDeviceModelSummaryResponse> minimum = service.list(
                null, null, null, null, null, ModelChecklistResearchStatus.FAILED, 0, 0, null);

        assertThat(clamped.getContent()).isEmpty();
        assertThat(clamped.getPage()).isEqualTo(3);
        assertThat(clamped.getSize()).isEqualTo(100);
        assertThat(clamped.getTotalElements()).isZero();
        assertThat(clamped.getTotalPages()).isZero();
        assertThat(clamped.isHasNext()).isFalse();
        assertThat(defaulted.getPage()).isZero();
        assertThat(defaulted.getSize()).isEqualTo(20);
        assertThat(minimum.getSize()).isEqualTo(1);
        verifyNoInteractions(modelRepository);
    }

    @Test
    void listMapsSummariesWithResearchStatusAndRelatedProductCount() {
        DeviceModel model = mock(DeviceModel.class);
        DeviceCategory category = mock(DeviceCategory.class);
        DeviceModelListingCountProjection countProjection =
                mock(DeviceModelListingCountProjection.class);
        when(model.getId()).thenReturn(101L);
        when(model.getCategory()).thenReturn(category);
        when(category.getId()).thenReturn(3L);
        when(category.getName()).thenReturn("스마트폰");
        when(model.manufacturerName()).thenReturn("Samsung");
        when(model.getModelName()).thenReturn("Galaxy S24");
        when(model.getModelCode()).thenReturn("SM-S921N");
        when(model.getOsFamily()).thenReturn(OsFamily.ANDROID);
        when(model.getReviewStatus()).thenReturn(DeviceModelReviewStatus.VERIFIED);
        when(model.getSourceType()).thenReturn(DeviceModelSourceType.CATALOG);
        when(model.isActive()).thenReturn(true);
        when(countProjection.getDeviceModelId()).thenReturn(101L);
        when(countProjection.getListingCount()).thenReturn(4L);
        when(modelRepository.findAll(
                        ArgumentMatchers.<Specification<DeviceModel>>any(),
                        eq(PageRequest.of(0, 20, DEFAULT_MODEL_SORT))))
                .thenReturn(new PageImpl<>(
                        List.of(model), PageRequest.of(0, 20, DEFAULT_MODEL_SORT), 1));
        when(listingRepository.countByDeviceModelIds(List.of(101L)))
                .thenReturn(List.of(countProjection));
        when(researchService.latestSummaries(Set.of(101L)))
                .thenReturn(Map.of(
                        101L,
                        new ModelChecklistResearchService.LatestResearchSummary(
                                ModelChecklistResearchStatus.APPROVED, 2)));

        PageResponse<AdminDeviceModelSummaryResponse> result =
                service.list(null, null, null, null, null, null, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        AdminDeviceModelSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.modelId()).isEqualTo(101L);
        assertThat(summary.categoryId()).isEqualTo(3L);
        assertThat(summary.categoryName()).isEqualTo("스마트폰");
        assertThat(summary.manufacturer()).isEqualTo("Samsung");
        assertThat(summary.osFamily()).isEqualTo("ANDROID");
        assertThat(summary.reviewStatus()).isEqualTo("VERIFIED");
        assertThat(summary.sourceType()).isEqualTo("CATALOG");
        assertThat(summary.isActive()).isTrue();
        assertThat(summary.latestResearchStatus()).isEqualTo("APPROVED");
        assertThat(summary.latestResearchVersion()).isEqualTo(2);
        assertThat(summary.relatedProductCount()).isEqualTo(4L);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listLeavesResearchAndProductCountUnsetWhenNoAggregateExists() {
        DeviceModel model = mock(DeviceModel.class);
        DeviceCategory category = mock(DeviceCategory.class);
        when(model.getId()).thenReturn(101L);
        when(model.getCategory()).thenReturn(category);
        when(model.getReviewStatus()).thenReturn(DeviceModelReviewStatus.PENDING_REVIEW);
        when(model.getSourceType()).thenReturn(DeviceModelSourceType.USER_REPORT);
        when(model.getOsFamily()).thenReturn(null);
        when(modelRepository.findAll(
                        ArgumentMatchers.<Specification<DeviceModel>>any(),
                        eq(PageRequest.of(0, 20, DEFAULT_MODEL_SORT))))
                .thenReturn(new PageImpl<>(
                        List.of(model), PageRequest.of(0, 20, DEFAULT_MODEL_SORT), 1));
        when(listingRepository.countByDeviceModelIds(List.of(101L))).thenReturn(List.of());
        when(researchService.latestSummaries(Set.of(101L))).thenReturn(Map.of());

        AdminDeviceModelSummaryResponse summary =
                service.list(null, null, null, null, null, null, 0, 20, null)
                        .getContent()
                        .get(0);

        assertThat(summary.osFamily()).isNull();
        assertThat(summary.latestResearchStatus()).isNull();
        assertThat(summary.latestResearchVersion()).isNull();
        assertThat(summary.relatedProductCount()).isZero();
    }

    @Test
    void listAppliesTheRequestedModelSortOrder() {
        when(modelRepository.findAll(
                        ArgumentMatchers.<Specification<DeviceModel>>any(),
                        ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null, null, null, 0, 20, "createdAt,desc");
        service.list(null, null, null, null, null, null, 0, 20, "  modelName,asc  ");
        service.list(null, null, null, null, null, null, 0, 20, "unsupported");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(modelRepository, times(3))
                .findAll(ArgumentMatchers.<Specification<DeviceModel>>any(), captor.capture());
        assertThat(captor.getAllValues().get(0).getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(captor.getAllValues().get(1).getSort())
                .isEqualTo(Sort.by(Sort.Order.asc("modelName"), Sort.Order.asc("id")));
        assertThat(captor.getAllValues().get(2).getSort()).isEqualTo(DEFAULT_MODEL_SORT);
    }

    @Test
    void listBuildsEveryOptionalFilterWhenAllCriteriaAreProvided() {
        when(researchService.modelIdsByLatestStatus(ModelChecklistResearchStatus.APPROVED))
                .thenReturn(Set.of(101L));
        when(modelRepository.findAll(
                        ArgumentMatchers.<Specification<DeviceModel>>any(),
                        eq(PageRequest.of(0, 20, DEFAULT_MODEL_SORT))))
                .thenReturn(new PageImpl<>(
                        List.of(), PageRequest.of(0, 20, DEFAULT_MODEL_SORT), 0));

        PageResponse<AdminDeviceModelSummaryResponse> result = service.list(
                " galaxy ",
                3L,
                9L,
                Boolean.TRUE,
                DeviceModelReviewStatus.VERIFIED,
                ModelChecklistResearchStatus.APPROVED,
                0,
                20,
                null);

        assertThat(result.getContent()).isEmpty();
        verify(modelRepository)
                .findAll(
                        ArgumentMatchers.<Specification<DeviceModel>>any(),
                        eq(PageRequest.of(0, 20, DEFAULT_MODEL_SORT)));
    }

    @Test
    void detailRejectsUnknownModelId() {
        when(modelRepository.findWithCatalogById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(templateRepository);
    }

    @Test
    void detailReturnsEmptyBaseItemsWhenNoPublishedTemplateExists() {
        stubDetailModel(101L);
        ListingStatusCountProjection countProjection = mock(ListingStatusCountProjection.class);
        when(countProjection.getStatus()).thenReturn(ListingStatus.ON_SALE);
        when(countProjection.getListingCount()).thenReturn(3L);
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(listingRepository.countStatusesByDeviceModelId(101L))
                .thenReturn(List.of(countProjection));
        when(researchService.countByModelId(101L)).thenReturn(2L);
        when(variantRepository.countByModelId(101L)).thenReturn(5L);

        AdminDeviceModelDetailResponse result = service.detail(101L);

        assertThat(result.modelId()).isEqualTo(101L);
        assertThat(result.categoryName()).isEqualTo("스마트폰");
        assertThat(result.osFamily()).isEqualTo("ANDROID");
        assertThat(result.baseChecklistItems()).isEmpty();
        assertThat(result.latestResearch()).isNull();
        assertThat(result.impact().productCount()).isEqualTo(3L);
        assertThat(result.impact().productStatusCounts())
                .containsEntry("ON_SALE", 3L)
                .containsEntry("DRAFT", 0L)
                .hasSize(ListingStatus.values().length);
        assertThat(result.impact().researchCount()).isEqualTo(2L);
        assertThat(result.impact().variantCount()).isEqualTo(5L);
        verifyNoInteractions(templateItemRepository);
    }

    @Test
    void detailMapsPublishedBaseChecklistItems() {
        stubDetailModel(101L);
        ChecklistTemplate template = mock(ChecklistTemplate.class);
        ChecklistTemplateItem item = mock(ChecklistTemplateItem.class);
        when(template.getId()).thenReturn(501L);
        when(item.getItemCode()).thenReturn("SP-DSP-001");
        when(item.getName()).thenReturn("화면 전체 터치");
        when(item.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(item.isRequired()).thenReturn(true);
        when(item.isVisibleToBuyer()).thenReturn(true);
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));
        when(templateItemRepository.findByChecklistTemplateIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(item));

        AdminDeviceModelDetailResponse result = service.detail(101L);

        assertThat(result.baseChecklistItems()).hasSize(1);
        assertThat(result.baseChecklistItems().get(0).getItemCode()).isEqualTo("SP-DSP-001");
        assertThat(result.baseChecklistItems().get(0).getEvidenceType()).isEqualTo("VIDEO");
        assertThat(result.baseChecklistItems().get(0).isRequired()).isTrue();
        assertThat(result.baseChecklistItems().get(0).isRecaptureAllowed()).isTrue();
        assertThat(result.baseChecklistItems().get(0).isVisibleToBuyer()).isTrue();
    }

    private void stubDetailModel(Long modelId) {
        DeviceModel model = mock(DeviceModel.class);
        DeviceCategory category = mock(DeviceCategory.class);
        when(model.getId()).thenReturn(modelId);
        when(model.getCategory()).thenReturn(category);
        when(category.getName()).thenReturn("스마트폰");
        when(model.getOsFamily()).thenReturn(OsFamily.ANDROID);
        when(model.getReviewStatus()).thenReturn(DeviceModelReviewStatus.VERIFIED);
        when(model.getSourceType()).thenReturn(DeviceModelSourceType.CATALOG);
        when(modelRepository.findWithCatalogById(modelId)).thenReturn(Optional.of(model));
    }

    @Test
    void impactStartsEveryListingStatusAtZeroWhenNoListingExists() {
        when(listingRepository.countStatusesByDeviceModelId(101L)).thenReturn(List.of());

        AdminDeviceModelImpactResponse result = service.impact(101L);

        assertThat(result.productCount()).isZero();
        assertThat(result.productStatusCounts()).hasSize(ListingStatus.values().length);
        assertThat(result.productStatusCounts().values()).allMatch(count -> count == 0L);
    }

    @Test
    void productsRejectsUnknownModelId() {
        when(modelRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.products(404L, 0, 20, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(listingRepository);
    }

    @Test
    void productsMapsRelatedListingsUsingTheDefaultSort() {
        Listing listing = mock(Listing.class);
        when(listing.getId()).thenReturn(5001L);
        when(listing.getSellerId()).thenReturn(11L);
        when(listing.getTitle()).thenReturn("Galaxy S24 256GB");
        when(listing.getPrice()).thenReturn(700_000L);
        when(listing.getStatus()).thenReturn(ListingStatus.ON_SALE);
        when(listing.getChecklistTemplateId()).thenReturn(501L);
        when(listing.isPrecheckCompleted()).thenReturn(true);
        when(modelRepository.existsById(101L)).thenReturn(true);
        when(listingRepository.findByDeviceModelIdAndDeletedAtIsNull(
                        101L, PageRequest.of(0, 20, DEFAULT_MODEL_SORT)))
                .thenReturn(new PageImpl<>(
                        List.of(listing), PageRequest.of(0, 20, DEFAULT_MODEL_SORT), 1));

        PageResponse<AdminRelatedProductResponse> result =
                service.products(101L, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        AdminRelatedProductResponse product = result.getContent().get(0);
        assertThat(product.productId()).isEqualTo(5001L);
        assertThat(product.sellerId()).isEqualTo(11L);
        assertThat(product.title()).isEqualTo("Galaxy S24 256GB");
        assertThat(product.price()).isEqualTo(700_000L);
        assertThat(product.status()).isEqualTo("ON_SALE");
        assertThat(product.checklistTemplateId()).isEqualTo(501L);
        assertThat(product.precheckCompleted()).isTrue();
    }

    @Test
    void productsAppliesCreatedAtSortWhenRequested() {
        Sort expected = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        when(modelRepository.existsById(101L)).thenReturn(true);
        when(listingRepository.findByDeviceModelIdAndDeletedAtIsNull(
                        101L, PageRequest.of(0, 20, expected)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20, expected), 0));

        assertThat(service.products(101L, 0, 20, " createdAt,desc ").getContent()).isEmpty();

        verify(listingRepository)
                .findByDeviceModelIdAndDeletedAtIsNull(
                        101L, PageRequest.of(0, 20, expected));
    }

    @Test
    void researchesRejectsUnknownModelId() {
        when(modelRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.researches(404L, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(researchService);
    }

    @Test
    void researchesDelegatesHistoryLookupToTheResearchService() {
        PageResponse<ChecklistResearchResponse> history =
                new PageResponse<>(List.of(), 0, 20, 0, 0, false);
        when(modelRepository.existsById(101L)).thenReturn(true);
        when(researchService.history(101L, 0, 20)).thenReturn(history);

        assertThat(service.researches(101L, 0, 20)).isSameAs(history);
    }

    @Test
    void materialsRejectsMissingProduct() {
        when(listingRepository.findByIdAndDeletedAtIsNull(5001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.materials(101L, 5001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));

        verifyNoInteractions(imageRepository);
    }

    @Test
    void materialsRejectsProductThatBelongsToAnotherModel() {
        Listing listing = mock(Listing.class);
        when(listing.getDeviceModelId()).thenReturn(999L);
        when(listingRepository.findByIdAndDeletedAtIsNull(5001L))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.materials(101L, 5001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));

        verifyNoInteractions(imageRepository);
    }

    @Test
    void materialsNumbersEvidenceAttemptsAndMarksTheLatestOne() {
        Listing listing = mock(Listing.class);
        ListingImage image = mock(ListingImage.class);
        ListingChecklistItem itemWithEvidence = mock(ListingChecklistItem.class);
        ListingChecklistItem itemWithoutEvidence = mock(ListingChecklistItem.class);
        Evidence first = mock(Evidence.class);
        Evidence second = mock(Evidence.class);
        LocalDateTime uploadedFirst = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime uploadedSecond = LocalDateTime.of(2026, 1, 1, 11, 0);

        when(listing.getId()).thenReturn(5001L);
        when(listing.getDeviceModelId()).thenReturn(101L);
        when(image.getId()).thenReturn(8001L);
        when(image.getImageType()).thenReturn(ListingImageType.THUMBNAIL);
        when(image.getDisplayOrder()).thenReturn(0);
        when(image.getMimeType()).thenReturn("image/jpeg");
        when(image.getS3Key()).thenReturn("images/8001.jpg");
        when(image.getCdnUrl()).thenReturn(null);
        when(itemWithEvidence.getId()).thenReturn(7002L);
        when(itemWithEvidence.getItemCode()).thenReturn("SP-DSP-002");
        when(itemWithEvidence.getName()).thenReturn("화면 전체 터치");
        when(itemWithEvidence.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(itemWithEvidence.getCompletionStatus())
                .thenReturn(ChecklistItemCompletionStatus.COMPLETED);
        when(itemWithEvidence.isRequired()).thenReturn(true);
        when(itemWithoutEvidence.getId()).thenReturn(7003L);
        when(itemWithoutEvidence.getItemCode()).thenReturn("SP-DSP-003");
        when(itemWithoutEvidence.getName()).thenReturn("배터리 상태");
        when(itemWithoutEvidence.getEvidenceType()).thenReturn(EvidenceType.PHOTO);
        when(itemWithoutEvidence.getCompletionStatus())
                .thenReturn(ChecklistItemCompletionStatus.PENDING);
        when(first.getId()).thenReturn(9001L);
        when(first.getListingChecklistItem()).thenReturn(itemWithEvidence);
        when(first.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(first.getProcessingStatus()).thenReturn(EvidenceProcessingStatus.FAILED);
        when(first.getS3Key()).thenReturn("evidence/9001.mp4");
        when(first.getUploadedAt()).thenReturn(uploadedFirst);
        when(first.getCapturedAt()).thenReturn(null);
        when(second.getId()).thenReturn(9002L);
        when(second.getListingChecklistItem()).thenReturn(itemWithEvidence);
        when(second.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(second.getProcessingStatus()).thenReturn(EvidenceProcessingStatus.READY);
        when(second.getS3Key()).thenReturn("evidence/9002.mp4");
        when(second.getUploadedAt()).thenReturn(uploadedSecond);
        when(second.getCapturedAt()).thenReturn(uploadedSecond.minusMinutes(5));
        when(listingRepository.findByIdAndDeletedAtIsNull(5001L))
                .thenReturn(Optional.of(listing));
        when(imageRepository.findAllByListingIdOrderByDisplayOrderAscIdAsc(5001L))
                .thenReturn(List.of(image));
        when(listingChecklistItemRepository.findAllByListingIdOrderByDisplayOrderAsc(5001L))
                .thenReturn(List.of(itemWithEvidence, itemWithoutEvidence));
        when(evidenceRepository.findAllByListingId(5001L)).thenReturn(List.of(second, first));
        when(mediaUrlResolver.resolve("images/8001.jpg", null))
                .thenReturn("https://cdn.example.com/images/8001.jpg");
        when(mediaUrlResolver.resolve("evidence/9001.mp4", null))
                .thenReturn("https://cdn.example.com/evidence/9001.mp4");
        when(mediaUrlResolver.resolve("evidence/9002.mp4", null))
                .thenReturn("https://cdn.example.com/evidence/9002.mp4");

        AdminProductMaterialsResponse result = service.materials(101L, 5001L);

        assertThat(result.productId()).isEqualTo(5001L);
        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).getImageType()).isEqualTo("THUMBNAIL");
        assertThat(result.images().get(0).getImageUrl())
                .isEqualTo("https://cdn.example.com/images/8001.jpg");
        assertThat(result.checklistItems()).hasSize(2);
        assertThat(result.checklistItems().get(0).completionStatus()).isEqualTo("COMPLETED");
        assertThat(result.checklistItems().get(0).isRequired()).isTrue();
        assertThat(result.checklistItems().get(0).evidence()).hasSize(2);
        assertThat(result.checklistItems().get(0).evidence().get(0).getEvidenceId())
                .isEqualTo(9001L);
        assertThat(result.checklistItems().get(0).evidence().get(0).getAttemptNo()).isEqualTo(1);
        assertThat(result.checklistItems().get(0).evidence().get(0).isLatest()).isFalse();
        assertThat(result.checklistItems().get(0).evidence().get(0).getCapturedAt()).isNull();
        assertThat(result.checklistItems().get(0).evidence().get(1).getEvidenceId())
                .isEqualTo(9002L);
        assertThat(result.checklistItems().get(0).evidence().get(1).getAttemptNo()).isEqualTo(2);
        assertThat(result.checklistItems().get(0).evidence().get(1).isLatest()).isTrue();
        assertThat(result.checklistItems().get(0).evidence().get(1).getProcessingStatus())
                .isEqualTo("READY");
        assertThat(result.checklistItems().get(0).evidence().get(1).getCapturedAt()).isNotNull();
        assertThat(result.checklistItems().get(1).checklistItemId()).isEqualTo(7003L);
        assertThat(result.checklistItems().get(1).evidence()).isEmpty();
        assertThat(result.checklistItems().get(1).isRequired()).isFalse();
    }

    @Test
    void updateFinishesTheReportedModelReviewWhenAPendingReportExists() {
        DeviceModelRequest report = mock(DeviceModelRequest.class);
        UpdateDeviceModelRequest update = new UpdateDeviceModelRequest(
                3L, "Samsung", "Galaxy S24", "SM-S921N", OsFamily.ANDROID);
        when(report.getId()).thenReturn(77L);
        when(report.getStatus()).thenReturn(DeviceModelRequestStatus.PENDING);
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.of(report));
        doReturn(null).when(service).detail(101L);

        service.update(101L, 9L, update);

        verify(requestService).update(77L, 9L, update);
        verify(requestService).approve(77L, 9L, "관리자 모델 정보 수정 완료");
        verifyNoInteractions(categoryRepository);
        verifyNoInteractions(catalogRegistrar);
    }

    @Test
    void updateIgnoresAlreadyResolvedReportsAndRejectsANonTopLevelCategory() {
        DeviceModelRequest report = mock(DeviceModelRequest.class);
        Category notTopLevel = mock(Category.class);
        when(report.getStatus()).thenReturn(DeviceModelRequestStatus.APPROVED);
        when(notTopLevel.getParent()).thenReturn(mock(Category.class));
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.of(report));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(notTopLevel));

        assertThatThrownBy(() -> service.update(
                        101L,
                        9L,
                        new UpdateDeviceModelRequest(
                                3L, "Samsung", "Galaxy S24", "SM-S921N", OsFamily.ANDROID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoInteractions(requestService);
    }

    @Test
    void updateRejectsAnInactiveTargetCategory() {
        Category inactive = mock(Category.class);
        when(inactive.isActive()).thenReturn(false);
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.update(
                        101L,
                        9L,
                        new UpdateDeviceModelRequest(
                                3L, "Samsung", "Galaxy S24", "SM-S921N", OsFamily.ANDROID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void updateRejectsAModelIdThatIsNotALegacyLeafCategory() {
        Category parent = mock(Category.class);
        when(parent.isActive()).thenReturn(true);
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        101L,
                        9L,
                        new UpdateDeviceModelRequest(
                                3L, "Samsung", "Galaxy S24", "SM-S921N", OsFamily.ANDROID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));

        verifyNoInteractions(catalogRegistrar);
    }

    @Test
    void updateRejectsADuplicateManufacturerAndModelCodePair() {
        Category parent = mock(Category.class);
        Category legacyModel = mock(Category.class);
        when(parent.isActive()).thenReturn(true);
        when(parent.getId()).thenReturn(3L);
        when(legacyModel.getParent()).thenReturn(parent);
        when(legacyModel.getModelCode()).thenReturn("SM-S921N");
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(legacyModel));
        when(modelRepository.existsByManufacturerIdAndModelCodeAndIdNot(
                        Manufacturer.idOf("Samsung"), "SM-S921N", 101L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(
                        101L,
                        9L,
                        new UpdateDeviceModelRequest(
                                3L, "Samsung", "Galaxy S24", "  ", OsFamily.ANDROID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(legacyModel, never()).updateLeaf(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any());
        verifyNoInteractions(catalogRegistrar);
    }

    @Test
    void updateKeepsTheBaseTemplateWhenTheCategoryDoesNotChange() {
        Category parent = mock(Category.class);
        Category legacyModel = mock(Category.class);
        UpdateDeviceModelRequest update = new UpdateDeviceModelRequest(
                3L, "Samsung", "Galaxy S24 Ultra", "SM-S928N", OsFamily.ANDROID);
        when(parent.isActive()).thenReturn(true);
        when(parent.getId()).thenReturn(3L);
        when(legacyModel.getParent()).thenReturn(parent);
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(legacyModel));
        when(modelRepository.existsByManufacturerIdAndModelCodeAndIdNot(
                        Manufacturer.idOf("Samsung"), "SM-S928N", 101L))
                .thenReturn(false);
        doReturn(null).when(service).detail(101L);

        service.update(101L, 9L, update);

        verify(legacyModel)
                .updateLeaf(parent, "Galaxy S24 Ultra", "Samsung", OsFamily.ANDROID, "SM-S928N");
        verify(catalogRegistrar).update(legacyModel);
        verify(catalogRegistrar).completeReview(101L, 9L, "관리자 모델 정보 수정 완료");
        verify(actionLogRepository).save(ArgumentMatchers.any());
        verifyNoInteractions(templateRepository);
        verifyNoInteractions(templateItemRepository);
    }

    @Test
    void updateFailsWhenTheNewCategoryHasNoSiblingTemplateToCopy() {
        Category parent = mock(Category.class);
        Category previousParent = mock(Category.class);
        Category legacyModel = mock(Category.class);
        Category sibling = mock(Category.class);
        when(parent.isActive()).thenReturn(true);
        when(parent.getId()).thenReturn(3L);
        when(previousParent.getId()).thenReturn(2L);
        when(legacyModel.getId()).thenReturn(101L);
        when(legacyModel.getParent()).thenReturn(previousParent);
        when(sibling.getId()).thenReturn(999L);
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(legacyModel));
        when(modelRepository.existsByManufacturerIdAndModelCodeAndIdNot(
                        Manufacturer.idOf("Samsung"), "SM-S928N", 101L))
                .thenReturn(false);
        when(categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(3L))
                .thenReturn(List.of(sibling));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        999L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        101L,
                        9L,
                        new UpdateDeviceModelRequest(
                                3L, "Samsung", "Galaxy S24 Ultra", "SM-S928N", OsFamily.ANDROID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));

        verifyNoInteractions(actionLogRepository);
    }

    @Test
    void updatePublishesACopiedBaseTemplateWhenTheCategoryChanges() {
        Category parent = mock(Category.class);
        Category previousParent = mock(Category.class);
        Category legacyModel = mock(Category.class);
        Category sibling = mock(Category.class);
        Category self = mock(Category.class);
        ChecklistTemplate source = mock(ChecklistTemplate.class);
        ChecklistTemplate previous = mock(ChecklistTemplate.class);
        ChecklistTemplateItem sourceItem = mock(ChecklistTemplateItem.class);
        ChecklistTemplate target = ChecklistTemplate.createDraft(101L, 3);
        when(parent.isActive()).thenReturn(true);
        when(parent.getId()).thenReturn(3L);
        when(previousParent.getId()).thenReturn(2L);
        when(legacyModel.getId()).thenReturn(101L);
        when(legacyModel.getParent()).thenReturn(previousParent);
        when(self.getId()).thenReturn(101L);
        when(sibling.getId()).thenReturn(999L);
        when(source.getId()).thenReturn(500L);
        when(previous.getVersion()).thenReturn(2);
        when(sourceItem.getItemCode()).thenReturn("SP-DSP-001");
        when(sourceItem.getName()).thenReturn("화면 전체 터치");
        when(sourceItem.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(requestRepository.findFirstByResolvedModelIdOrderByCreatedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(legacyModel));
        when(modelRepository.existsByManufacturerIdAndModelCodeAndIdNot(
                        Manufacturer.idOf("Samsung"), "SM-S928N", 101L))
                .thenReturn(false);
        when(categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(3L))
                .thenReturn(List.of(self, sibling));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        999L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(source));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(previous));
        when(templateRepository.saveAndFlush(ArgumentMatchers.<ChecklistTemplate>any()))
                .thenReturn(target);
        when(templateItemRepository.findByChecklistTemplateIdOrderByDisplayOrderAsc(500L))
                .thenReturn(List.of(sourceItem));
        doReturn(null).when(service).detail(101L);

        service.update(
                101L,
                9L,
                new UpdateDeviceModelRequest(
                        3L, "Samsung", "Galaxy S24 Ultra", "SM-S928N", OsFamily.ANDROID));

        ArgumentCaptor<ChecklistTemplate> draftCaptor =
                ArgumentCaptor.forClass(ChecklistTemplate.class);
        verify(templateRepository).saveAndFlush(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getCategoryId()).isEqualTo(101L);
        assertThat(draftCaptor.getValue().getVersion()).isEqualTo(3);
        assertThat(target.getStatus()).isEqualTo(ChecklistTemplateStatus.PUBLISHED);
        verify(templateItemRepository)
                .saveAllAndFlush(ArgumentMatchers.<List<ChecklistTemplateItem>>any());
        verify(actionLogRepository).save(ArgumentMatchers.any());
    }
}
