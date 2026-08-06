package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.repository.ListingChecklistCountProjection;
import com.c203.limit.domain.inspection.agent.InspectionSessionTestResultRepository;
import com.c203.limit.domain.inspection.checklist.ChecklistGenerationService;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklist;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklistItem;
import com.c203.limit.domain.inspection.checklist.LaptopChecklistPolicy;
import com.c203.limit.domain.inspection.checklist.LaptopFeatureCode;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.ChecklistItemOrigin;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductRequest;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.ListingStatusHistory;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.ListingThumbnailProjection;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.repository.ProductEngagementReader;
import com.c203.limit.domain.product.repository.ProductEngagementReader.ProductEngagement;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.domain.rtc.repository.RtcSessionChecklistResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTests {
    @Mock ListingRepository listingRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ChecklistTemplateRepository templateRepository;
    @Mock ChecklistTemplateItemRepository templateItemRepository;
    @Mock ListingChecklistItemRepository checklistItemRepository;
    @Mock ListingStatusHistoryRepository statusHistoryRepository;
    @Mock ListingImageRepository imageRepository;
    @Mock ProductViewCountDispatcher viewCountDispatcher;
    @Mock ProductEngagementReader engagementReader;
    @Mock ChecklistGenerationService checklistGenerationService;
    @Mock EvidenceRepository evidenceRepository;
    @Mock ReinspectionRequestItemRepository reinspectionRequestItemRepository;
    @Mock RtcSessionChecklistResultRepository rtcSessionChecklistResultRepository;
    @Mock MediaUploadSessionRepository mediaUploadSessionRepository;
    @Mock InspectionSessionTestResultRepository inspectionSessionTestResultRepository;
    ProductApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProductApplicationService(
                listingRepository, categoryRepository, templateRepository, templateItemRepository,
                checklistItemRepository, statusHistoryRepository, imageRepository, null,
                checklistGenerationService, evidenceRepository, reinspectionRequestItemRepository,
                rtcSessionChecklistResultRepository, mediaUploadSessionRepository,
                inspectionSessionTestResultRepository, viewCountDispatcher, engagementReader);
        // 상세를 만드는 모든 경로가 관심도 수치를 읽는다. 이 테스트들이 확인하는 것은
        // 그 수치가 아니라 모델명·상태 처리라, 값은 0으로 두고 NPE만 막는다.
        lenient().when(engagementReader.findByListingId(anyLong()))
                .thenReturn(new ProductEngagement(0L, 0L));
    }

    @Test
    void createsDraftAndSnapshotsPublishedChecklist() {
        Category model = model();
        ChecklistTemplate template = template();
        ChecklistTemplateItem required = ChecklistTemplateItem.create(
                template, "EXT-01", "외관", "외관 확인", "전체 촬영", EvidenceType.PHOTO,
                AutomationType.NONE, true, 1);
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));
        when(templateItemRepository.findByChecklistTemplateIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(required));
        when(listingRepository.saveAndFlush(any(Listing.class))).thenAnswer(invocation -> {
            Listing listing = invocation.getArgument(0);
            ReflectionTestUtils.setField(listing, "id", 1001L);
            return listing;
        });

        var result = service.create(55L, new CreateProductRequest(
                1L, 101L, "Galaxy S24", "상태 양호", BigDecimal.valueOf(650000),
                "Black", 256, "서울 강남구"));

        assertThat(result.getProductId()).isEqualTo(1001L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getRequiredItemCount()).isEqualTo(1);
        verify(checklistItemRepository).saveAll(any());
    }

    @Test
    void createsListingSpecificSnapshotForGeneratedLaptopChecklist() {
        Category model = laptopModel();
        var generatedItems =
                new LaptopChecklistPolicy()
                        .generate(OsFamily.WINDOWS, Set.of(LaptopFeatureCode.CAMERA));
        var generated = new GeneratedChecklist(
                201L,
                "Samsung",
                "Galaxy Book4 Pro",
                OsFamily.WINDOWS,
                1,
                false,
                generatedItems,
                List.of(),
                List.of());
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(model));
        when(templateRepository.saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> {
                    ChecklistTemplate template = invocation.getArgument(0);
                    ReflectionTestUtils.setField(template, "id", 601L);
                    return template;
                });
        when(templateItemRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(listingRepository.saveAndFlush(any(Listing.class)))
                .thenAnswer(invocation -> {
                    Listing listing = invocation.getArgument(0);
                    ReflectionTestUtils.setField(listing, "id", 2001L);
                    return listing;
                });

        var result = service.create(
                55L,
                new CreateProductRequest(
                        2L,
                        201L,
                        "Galaxy Book4 Pro",
                        "상태 양호",
                        BigDecimal.valueOf(950000),
                        "Gray",
                        512,
                        "서울"),
                generated);

        assertThat(result.getProductId()).isEqualTo(2001L);
        assertThat(result.getRequiredItemCount()).isEqualTo(14);
        verify(templateRepository).saveAndFlush(any(ChecklistTemplate.class));
        verify(templateItemRepository).saveAllAndFlush(any());
        verify(checklistItemRepository).saveAll(any());
    }

    @Test
    void rejectsModelOutsideSelectedCategory() {
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model()));

        assertThatThrownBy(() -> service.create(55L, new CreateProductRequest(
                999L, 101L, "Galaxy S24", null, BigDecimal.ONE, null, 256, "서울")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    @Test
    void publishesEvenWhenRequiredChecklistIsIncomplete() {
        Listing listing = listing();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(statusHistoryRepository.saveAndFlush(any(ListingStatusHistory.class)))
                .thenAnswer(invocation -> {
                    ListingStatusHistory history = invocation.getArgument(0);
                    ReflectionTestUtils.setField(history, "id", 7002L);
                    return history;
                });

        var result = service.transition(
                55L, 1001L, new TransitionProductStatusRequest("ON_SALE", "등록 완료"));

        assertThat(result.getCurrentStatus()).isEqualTo("ON_SALE");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void publishesWhenRequiredChecklistIsCompleteAndRecordsHistory() {
        Listing listing = listing();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(statusHistoryRepository.saveAndFlush(any(ListingStatusHistory.class)))
                .thenAnswer(invocation -> {
                    ListingStatusHistory history = invocation.getArgument(0);
                    ReflectionTestUtils.setField(history, "id", 7001L);
                    return history;
                });

        var result = service.transition(
                55L, 1001L, new TransitionProductStatusRequest("ON_SALE", "검수 완료"));

        assertThat(result.getCurrentStatus()).isEqualTo("ON_SALE");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void publicDetailDoesNotExposeDraftProduct() {
        when(listingRepository.findByIdAndStatusAndDeletedAtIsNull(
                        1001L, ListingStatus.ON_SALE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findPublicDetail(1001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
        verify(viewCountDispatcher, never()).dispatch(1001L);
    }

    @Test
    void publicDetailSurvivesRejectedViewCountDispatchAfterBuildingResponse() {
        Listing listing = listing();
        listing.completePrecheck();
        listing.publish();
        when(listingRepository.findByIdAndStatusAndDeletedAtIsNull(
                        1001L, ListingStatus.ON_SALE))
                .thenReturn(Optional.of(listing));
        when(checklistItemRepository.countRequiredByListingIds(
                        List.of(1001L),
                        ChecklistItemCompletionStatus.COMPLETED,
                        EvidenceType.SELLER_CONFIRMATION))
                .thenReturn(List.of());
        when(imageRepository.findFirstByListingIdsAndImageType(
                        List.of(1001L),
                        com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL))
                .thenReturn(List.of());
        doThrow(new org.springframework.core.task.TaskRejectedException("queue full"))
                .when(viewCountDispatcher)
                .dispatch(1001L);

        var result = service.findPublicDetail(1001L);

        assertThat(result.getProductId()).isEqualTo(1001L);
        verify(viewCountDispatcher).dispatch(1001L);
    }

    @Test
    void sellerCanReadOwnDraftThroughOwnedDetail() {
        Listing listing = listing();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 12, 0);
        ReflectionTestUtils.setField(listing, "createdAt", createdAt);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        var result = service.findOwnedDetail(55L, 1001L);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getCreatedAt())
                .isEqualTo(createdAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }

    @Test
    void clearsExplicitlyNullOptionalFields() throws Exception {
        Listing listing = listing();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        UpdateProductRequest request = new ObjectMapper().readValue(
                "{\"description\":null,\"color\":null,\"storageGb\":null}",
                UpdateProductRequest.class);

        service.update(55L, 1001L, request);

        assertThat(listing.getDescription()).isNull();
        assertThat(listing.getColor()).isNull();
        assertThat(listing.getStorageGb()).isNull();
    }

    // 카탈로그에 없는 기기는 '기타 (직접 입력)' 모델 한 행을 공유하므로, 실제 제조사·모델명을
    // 매물에 남기고 상세·목록에서 그 값을 노출해야 구매자가 기기를 특정할 수 있다.
    @Test
    void showsSellerEnteredModelInsteadOfThePlaceholderCatalogRow() {
        Listing listing = listing();
        listing.applyCustomModel("LG", "gram Pro 17");
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        var result = service.findOwnedDetail(55L, 1001L);

        assertThat(result.getDevice().getManufacturer()).isEqualTo("LG");
        assertThat(result.getDevice().getModel()).isEqualTo("gram Pro 17");
    }

    @Test
    void fallsBackToCatalogModelWhenSellerDidNotEnterOne() {
        Listing listing = listing();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        var result = service.findOwnedDetail(55L, 1001L);

        assertThat(result.getDevice().getModel()).isEqualTo("Galaxy S24");
    }

    @Test
    void trimsAndNullsOutBlankSellerEnteredModel() {
        Listing listing = listing();
        listing.applyCustomModel("  LG  ", "   ");

        assertThat(listing.getCustomManufacturer()).isEqualTo("LG");
        assertThat(listing.getCustomModelName()).isNull();
    }

    @Test
    void letsSellerCloseAnOnSaleListingAsSold() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ON_SALE);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(statusHistoryRepository.saveAndFlush(any(ListingStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.transition(
                55L, 1001L, new TransitionProductStatusRequest("SOLD", "직거래로 판매 완료"));

        assertThat(result.getCurrentStatus()).isEqualTo("SOLD");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void letsSellerCloseAHiddenListingAsSold() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.HIDDEN);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(statusHistoryRepository.saveAndFlush(any(ListingStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.transition(55L, 1001L, new TransitionProductStatusRequest("SOLD", "직거래"));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    // 예약 이후에는 구매자가 결제·검수에 들어가 있어 판매자가 임의로 닫으면 주문과 상태가 어긋난다.
    @Test
    void rejectsSellerSoldTransitionOnceBuyerHasReserved() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.RESERVED);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.transition(
                        55L, 1001L, new TransitionProductStatusRequest("SOLD", "직거래")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
    }

    // 직거래는 약속이 깨질 수 있어 판매자가 원래 글로 돌아갈 길이 필요하다.
    @Test
    void letsSellerReopenASoldListing() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.SOLD);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(statusHistoryRepository.saveAndFlush(any(ListingStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.transition(
                55L, 1001L, new TransitionProductStatusRequest("ON_SALE", "거래 파기로 판매 재개"));

        assertThat(result.getPreviousStatus()).isEqualTo("SOLD");
        assertThat(result.getCurrentStatus()).isEqualTo("ON_SALE");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    // 되돌린 매물은 다시 수정할 수 있어야 한다.
    @Test
    void allowsEditingAfterReopeningASoldListing() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.SOLD);
        listing.reopenSoldBySeller();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        UpdateProductRequest request = new UpdateProductRequest();
        request.setPrice(BigDecimal.valueOf(100000));

        service.update(55L, 1001L, request);

        assertThat(listing.getPrice()).isEqualTo(100000L);
    }

    @Test
    void rejectsReopeningAListingThatIsNotSold() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.HIDDEN);

        assertThatThrownBy(listing::reopenSoldBySeller)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));
    }

    @Test
    void rejectsEditingASoldListing() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.SOLD);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        UpdateProductRequest request = new UpdateProductRequest();
        request.setPrice(BigDecimal.valueOf(100000));

        assertThatThrownBy(() -> service.update(55L, 1001L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_EDIT_NOT_ALLOWED));
    }

    @Test
    void allowsEditingAListingThatIsAlreadyOnSale() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ON_SALE);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        UpdateProductRequest request = new UpdateProductRequest();
        request.setPrice(BigDecimal.valueOf(100000));

        service.update(55L, 1001L, request);

        assertThat(listing.getPrice()).isEqualTo(100000L);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void rejectsEditingOnceTheTradeHasStarted() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.RESERVED);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        UpdateProductRequest request = new UpdateProductRequest();
        request.setPrice(BigDecimal.valueOf(100000));

        assertThatThrownBy(() -> service.update(55L, 1001L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_EDIT_NOT_ALLOWED));
    }

    // 상품 관리 화면이 대표 이미지와 기기 정보를 비워 둔 채 보여 주던 문제. 값은 DB에 있었지만
    // 내 상품 목록 응답에만 실려 나가지 않았다.
    @Test
    void myProductListCarriesThumbnailAndDeviceInfo() {
        Listing listing = listing();
        when(listingRepository.findBySellerIdAndDeletedAtIsNull(eq(55L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));
        ListingThumbnailProjection thumbnail = mock(ListingThumbnailProjection.class);
        when(thumbnail.getListingId()).thenReturn(1001L);
        when(thumbnail.getCdnUrl()).thenReturn("https://cdn.example.com/1001.jpg");
        when(imageRepository.findFirstByListingIdsAndImageType(
                        eq(List.of(1001L)),
                        eq(com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL)))
                .thenReturn(List.of(thumbnail));

        var result = service.findMine(55L, null, 0, 20, "updatedAt,desc");

        assertThat(result.content()).hasSize(1);
        var item = result.content().get(0);
        assertThat(item.getThumbnailUrl()).isEqualTo("https://cdn.example.com/1001.jpg");
        assertThat(item.getManufacturerName()).isNotBlank();
        assertThat(item.getModelName()).isNotBlank();
        assertThat(item.getPrice()).isNotNull();
    }

    @Test
    void loadsChecklistCountsAndThumbnailsOnceForAProductPage() {
        Listing first = listing();
        ReflectionTestUtils.setField(first, "viewCount", 128L);
        Listing second = listing();
        ReflectionTestUtils.setField(second, "id", 1002L);
        var page = new PageImpl<>(List.of(first, second));
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        ListingChecklistCountProjection firstCount = mock(ListingChecklistCountProjection.class);
        when(firstCount.getListingId()).thenReturn(1001L);
        when(firstCount.getRequiredCount()).thenReturn(2L);
        when(firstCount.getCompletedRequiredCount()).thenReturn(2L);
        when(checklistItemRepository.countRequiredByListingIds(
                        eq(List.of(1001L, 1002L)), eq(ChecklistItemCompletionStatus.COMPLETED), eq(EvidenceType.SELLER_CONFIRMATION)))
                .thenReturn(List.of(firstCount));
        ListingThumbnailProjection thumbnail = mock(ListingThumbnailProjection.class);
        when(thumbnail.getListingId()).thenReturn(1001L);
        when(thumbnail.getCdnUrl()).thenReturn("https://cdn.example.com/1001.jpg");
        when(imageRepository.findFirstByListingIdsAndImageType(
                        eq(List.of(1001L, 1002L)), eq(com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL)))
                .thenReturn(List.of(thumbnail));

        var result = service.findPublic(
                null, null, null, null, null, null, null, null, null, null, null, 0, 20, "price,asc");

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).getVerificationStatus()).isEqualTo("COMPLETED");
        assertThat(result.content().get(0).getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/1001.jpg");
        assertThat(result.content().get(0).getViewCount()).isEqualTo(128L);
        verify(checklistItemRepository).countRequiredByListingIds(
                List.of(1001L, 1002L), ChecklistItemCompletionStatus.COMPLETED, EvidenceType.SELLER_CONFIRMATION);
        verify(imageRepository).findFirstByListingIdsAndImageType(
                List.of(1001L, 1002L),
                com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL);
        verify(listingRepository).findAll(
                any(Specification.class),
                argThat((Pageable pageable) ->
                        pageable.getSort().getOrderFor("price").isAscending()));
    }

    @Test
    void sortsPublicProductsByViewCountWithStableTieBreakers() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findPublic(
                null, null, null, null, null, null, null, null, null, null, null, 0, 20,
                "viewCount,desc");

        verify(listingRepository).findAll(
                any(Specification.class),
                argThat((Pageable pageable) -> {
                    var orders = pageable.getSort().stream().toList();
                    return orders.size() == 3
                            && orders.get(0).getProperty().equals("viewCount")
                            && orders.get(0).isDescending()
                            && orders.get(1).getProperty().equals("createdAt")
                            && orders.get(1).isDescending()
                            && orders.get(2).getProperty().equals("id")
                            && orders.get(2).isDescending();
                }));
    }

    @Test
    void rejectsUnsupportedVerificationStatusAndSort() {
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, "UNKNOWN", null, null, null, 0, 20,
                        "createdAt,desc"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, null, null, null, 0, 20,
                        "sellerId,asc"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void updateAddsConfirmedFeatureItemToOwnedDraftTemplate() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplate draftTemplate = ChecklistTemplate.createDraft(101L, 3);
        ReflectionTestUtils.setField(draftTemplate, "id", 501L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of("FINGERPRINT"))))
                .thenReturn(Map.of("FINGERPRINT", generatedItem("PHN-FTR-FP", "FINGERPRINT")));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of());
        when(templateRepository.findById(501L)).thenReturn(Optional.of(draftTemplate));
        when(listingRepository.countByChecklistTemplateId(501L)).thenReturn(1L);
        when(templateItemRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> {
                    List<ChecklistTemplateItem> items = invocation.getArgument(0);
                    items.forEach(item -> ReflectionTestUtils.setField(item, "id", 9001L));
                    return items;
                });

        service.update(55L, 1001L, updateRequestWithFeatures(Set.of("FINGERPRINT")));

        verify(templateRepository, never()).saveAndFlush(any(ChecklistTemplate.class));
        verify(checklistItemRepository).saveAll(argThat((List<ListingChecklistItem> saved) ->
                saved.size() == 1
                        && saved.get(0).getItemCode().equals("PHN-FTR-FP")
                        && saved.get(0).getItemOrigin() == ChecklistItemOrigin.CONFIRMED_FEATURE
                        && saved.get(0).getFeatureCode().equals("FINGERPRINT")));
        verify(checklistItemRepository, never()).deleteAll(anyList());
    }

    @Test
    void updatePromotesSharedPublishedTemplateToBespokeDraftWhenFeatureAdded() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplate publishedTemplate = template();
        ChecklistTemplate bespokeDraft = ChecklistTemplate.createDraft(101L, 3);
        ReflectionTestUtils.setField(bespokeDraft, "id", 901L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of("FINGERPRINT"))))
                .thenReturn(Map.of("FINGERPRINT", generatedItem("PHN-FTR-FP", "FINGERPRINT")));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of());
        when(templateRepository.findById(501L)).thenReturn(Optional.of(publishedTemplate));
        when(templateRepository.saveAndFlush(any(ChecklistTemplate.class))).thenReturn(bespokeDraft);
        when(templateItemRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> {
                    List<ChecklistTemplateItem> items = invocation.getArgument(0);
                    items.forEach(item -> ReflectionTestUtils.setField(item, "id", 9002L));
                    return items;
                });

        service.update(55L, 1001L, updateRequestWithFeatures(Set.of("FINGERPRINT")));

        verify(templateRepository).saveAndFlush(argThat(
                (ChecklistTemplate candidate) -> candidate.getStatus() == ChecklistTemplateStatus.DRAFT));
        assertThat(listing.getChecklistTemplateId()).isEqualTo(901L);
        verify(listingRepository, never()).countByChecklistTemplateId(any());
        // 새 전용 템플릿에는 이번에 추가되는 기능 정의만 필요하다 — 기본 항목은
        // listing_checklist_item.item_origin 스냅샷이 이미 보존한다.
        verify(templateItemRepository).saveAllAndFlush(argThat((List<ChecklistTemplateItem> items) ->
                items.size() == 1 && items.get(0).getItemCode().equals("PHN-FTR-FP")));
        verify(checklistItemRepository).saveAll(argThat((List<ListingChecklistItem> saved) ->
                saved.size() == 1
                        && saved.get(0).getItemCode().equals("PHN-FTR-FP")
                        && saved.get(0).getItemOrigin() == ChecklistItemOrigin.CONFIRMED_FEATURE));
    }

    @Test
    void updateRemovesUncheckedFeatureItemWithoutEvidence() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                template(), "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 2);
        ListingChecklistItem existing = ListingChecklistItem.createConfirmedFeatureItem(
                1001L, templateItem, "FINGERPRINT");
        ReflectionTestUtils.setField(existing, "id", 7001L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of())))
                .thenReturn(Map.of());
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(existing));

        service.update(55L, 1001L, updateRequestWithFeatures(Set.of()));

        verify(checklistItemRepository).deleteAll(List.of(existing));
        verify(checklistItemRepository, never()).saveAll(anyList());
        // toAddFeatures가 비어 있으면 템플릿을 건드릴 필요가 없다.
        verify(templateRepository, never()).findById(any());
    }

    @Test
    void updateBlocksRemovalWhenChecklistItemHasEvidence() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                template(), "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 2);
        ListingChecklistItem existing = ListingChecklistItem.createConfirmedFeatureItem(
                1001L, templateItem, "FINGERPRINT");
        ReflectionTestUtils.setField(existing, "id", 7002L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of())))
                .thenReturn(Map.of());
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(existing));
        when(evidenceRepository.existsByListingChecklistItem_Id(7002L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(55L, 1001L, updateRequestWithFeatures(Set.of())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_ITEM_LOCKED_BY_EVIDENCE));
        verify(checklistItemRepository, never()).deleteAll(anyList());
        verify(checklistItemRepository, never()).saveAll(anyList());
    }

    @Test
    void updateBlocksRemovalWhenMediaUploadSessionReferencesItem() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                template(), "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 2);
        ListingChecklistItem existing = ListingChecklistItem.createConfirmedFeatureItem(
                1001L, templateItem, "FINGERPRINT");
        ReflectionTestUtils.setField(existing, "id", 7003L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of())))
                .thenReturn(Map.of());
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(existing));
        when(mediaUploadSessionRepository.existsByChecklistItem_Id(7003L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(55L, 1001L, updateRequestWithFeatures(Set.of())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_ITEM_LOCKED_BY_EVIDENCE));
    }

    @Test
    void updateBlocksRemovalWhenInspectionSessionTestResultReferencesItem() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                template(), "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 2);
        ListingChecklistItem existing = ListingChecklistItem.createConfirmedFeatureItem(
                1001L, templateItem, "FINGERPRINT");
        ReflectionTestUtils.setField(existing, "id", 7005L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of())))
                .thenReturn(Map.of());
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(existing));
        when(inspectionSessionTestResultRepository.existsByChecklistItemId(7005L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(55L, 1001L, updateRequestWithFeatures(Set.of())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_ITEM_LOCKED_BY_EVIDENCE));
    }

    @Test
    void updateIsNoOpWhenConfirmedFeaturesUnchanged() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                template(), "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 2);
        ListingChecklistItem existing = ListingChecklistItem.createConfirmedFeatureItem(
                1001L, templateItem, "FINGERPRINT");
        ReflectionTestUtils.setField(existing, "id", 7004L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of("FINGERPRINT"))))
                .thenReturn(Map.of("FINGERPRINT", generatedItem("PHN-FTR-FP", "FINGERPRINT")));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(existing));

        service.update(55L, 1001L, updateRequestWithFeatures(Set.of("FINGERPRINT")));

        verify(checklistItemRepository, never()).saveAll(anyList());
        verify(checklistItemRepository, never()).deleteAll(anyList());
        verify(templateRepository, never()).findById(any());
    }

    @Test
    void updateAssignsNewFeatureItemDisplayOrderPastExistingGaps() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplate draftTemplate = ChecklistTemplate.createDraft(101L, 3);
        ReflectionTestUtils.setField(draftTemplate, "id", 501L);
        ChecklistTemplateItem baseTemplateItem = ChecklistTemplateItem.create(
                draftTemplate, "EXT-01", "외관", "확인", "가이드", EvidenceType.PHOTO,
                AutomationType.NONE, true, 5);
        ListingChecklistItem baseItem =
                ListingChecklistItem.createFromTemplateItem(1001L, baseTemplateItem);
        ReflectionTestUtils.setField(baseItem, "id", 7010L);
        ChecklistTemplateItem featureTemplateItem = ChecklistTemplateItem.create(
                draftTemplate, "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 9);
        ListingChecklistItem featureItem = ListingChecklistItem.createConfirmedFeatureItem(
                1001L, featureTemplateItem, "FINGERPRINT");
        ReflectionTestUtils.setField(featureItem, "id", 7011L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of("CAMERA"))))
                .thenReturn(Map.of("CAMERA", generatedItem("PHN-FTR-CAM", "CAMERA")));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(baseItem, featureItem));
        when(templateRepository.findById(501L)).thenReturn(Optional.of(draftTemplate));
        when(listingRepository.countByChecklistTemplateId(501L)).thenReturn(1L);
        when(templateItemRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> {
                    List<ChecklistTemplateItem> items = invocation.getArgument(0);
                    items.forEach(item -> ReflectionTestUtils.setField(item, "id", 9010L));
                    return items;
                });

        service.update(55L, 1001L, updateRequestWithFeatures(Set.of("CAMERA")));

        // 기존 항목의 최댓값(9)보다 큰 순번을 받아야 한다 — 개수 기반(currentItems.size()+1=3)이면
        // 기존 displayOrder 9와 겹칠 수 있다.
        verify(checklistItemRepository).saveAll(argThat((List<ListingChecklistItem> saved) ->
                saved.size() == 1 && saved.get(0).getDisplayOrder() == 10));
        verify(checklistItemRepository).deleteAll(List.of(featureItem));
    }

    @Test
    void updateRejectsConfirmedFeaturesForCustomModelListing() {
        Listing listing = listingWithDeviceModel();
        listing.applyCustomModel("Samsung", "직접 입력 모델");
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.update(55L, 1001L, updateRequestWithFeatures(Set.of("FINGERPRINT"))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOM_MODEL_CHECKLIST_EDIT_NOT_SUPPORTED));
        verify(checklistGenerationService, never()).resolveConfirmedFeatureItems(any(), any());
    }

    private UpdateProductRequest updateRequestWithFeatures(Set<String> features) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setConfirmedFeatures(features);
        return request;
    }

    private Listing listingWithDeviceModel() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "deviceModelId", 101L);
        return listing;
    }

    private GeneratedChecklistItem generatedItem(String itemCode, String featureCode) {
        return new GeneratedChecklistItem(
                itemCode, "지문 인식", "확인", "가이드", EvidenceType.VIDEO, AutomationType.NONE, null,
                true, 1, featureCode, null, null, null);
    }

    private Category model() {
        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(parent, "id", 1L);
        Category model = Category.createLeaf(
                parent, "Galaxy S24", DeviceType.SMARTPHONE, "Samsung", OsFamily.ANDROID,
                "SM-S921", List.of(128, 256, 512), 1);
        ReflectionTestUtils.setField(model, "id", 101L);
        return model;
    }

    private Category laptopModel() {
        Category parent = Category.createTopLevel("Laptop", DeviceType.LAPTOP, 1);
        ReflectionTestUtils.setField(parent, "id", 2L);
        Category model = Category.createLeaf(
                parent,
                "Galaxy Book4 Pro",
                DeviceType.LAPTOP,
                "Samsung",
                OsFamily.WINDOWS,
                "NT960",
                List.of(512, 1024),
                1);
        ReflectionTestUtils.setField(model, "id", 201L);
        return model;
    }

    private ChecklistTemplate template() {
        ChecklistTemplate template = ChecklistTemplate.createDraft(101L, 3);
        template.publish();
        ReflectionTestUtils.setField(template, "id", 501L);
        return template;
    }

    private Listing listing() {
        Listing listing = Listing.createDraft(
                55L, model(), "Galaxy S24", "상태 양호", 650000, "Black", 256,
                "서울 강남구", 501L);
        ReflectionTestUtils.setField(listing, "id", 1001L);
        return listing;
    }

    @Test
    void rejectsEditingAProductThatBelongsToAnotherSeller() {
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.empty());
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(listing()));

        assertThatThrownBy(() -> service.update(55L, 1001L, new UpdateProductRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
    }

    @Test
    void rejectsEditingAProductThatDoesNotExist() {
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.empty());
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(55L, 1001L, new UpdateProductRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
    }

    @Test
    void rejectsAPriceOutsideTheContractRange() {
        Listing listing = listing();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.update(55L, 1001L, updateRequestWithPrice(BigDecimal.ZERO)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.update(
                        55L,
                        1001L,
                        updateRequestWithPrice(BigDecimal.valueOf(1_000_000_000_000L))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(
                        55L, 1001L, updateRequestWithPrice(new BigDecimal("650000.50"))))
                .isInstanceOf(BusinessException.class);
        assertThat(listing.getPrice()).isEqualTo(650000L);
    }

    @Test
    void softDeletesAHiddenProduct() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.HIDDEN);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        service.delete(55L, 1001L);

        assertThat(listing.getDeletedAt()).isNotNull();
    }

    @Test
    void rejectsDeletingAProductThatIsAlreadyInATrade() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.RESERVED);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.delete(55L, 1001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_DELETE_NOT_ALLOWED));

        assertThat(listing.getDeletedAt()).isNull();
    }

    @Test
    void rejectsPagingParametersOutsideTheAllowedRangeOnThePublicList() {
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, null, null, null, -1, 20,
                        null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, null, null, null, 0, 0,
                        null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, null, null, null, 0, 101,
                        null))
                .isInstanceOf(BusinessException.class);

        verify(listingRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsPagingParametersOutsideTheAllowedRangeOnTheSellerList() {
        assertThatThrownBy(() -> service.findMine(55L, null, -1, 20, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.findMine(55L, null, 0, 101, null))
                .isInstanceOf(BusinessException.class);

        verify(listingRepository, never())
                .findBySellerIdAndDeletedAtIsNull(anyLong(), any(Pageable.class));
    }

    @Test
    void filtersTheSellerListByTheRequestedStatus() {
        when(listingRepository.findBySellerIdAndStatusAndDeletedAtIsNull(
                        eq(55L), eq(ListingStatus.ON_SALE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.findMine(55L, "ON_SALE", 0, 20, null);

        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        verify(listingRepository, never())
                .findBySellerIdAndDeletedAtIsNull(anyLong(), any(Pageable.class));
    }

    @Test
    void rejectsAnUnknownStatusFilterOnTheSellerList() {
        assertThatThrownBy(() -> service.findMine(55L, "NOT_A_STATUS", 0, 20, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void rejectsASortFieldThatIsNotAllowedForTheSellerList() {
        assertThatThrownBy(() -> service.findMine(55L, null, 0, 20, "viewCount,desc"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void treatsASortValueWithoutADirectionAsAscending() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findPublic(
                null, null, null, null, null, null, null, null, null, null, null, 0, 20, "price");

        verify(listingRepository).findAll(
                any(Specification.class),
                argThat((Pageable pageable) ->
                        pageable.getSort().getOrderFor("price").isAscending()));
    }

    @Test
    void rejectsMalformedSortExpressions() {
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, null, null, null, 0, 20,
                        "price,asc,extra"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, null, null, null, 0, 20,
                        "price,upward"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(listingRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsAnInvalidVerifiedCountRange() {
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, -1, null, null, 0, 20,
                        null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, 5, 3, null, 0, 20, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(listingRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsATargetStatusThatIsNotAKnownListingStatus() {
        Listing listing = listing();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.transition(
                        55L, 1001L, new TransitionProductStatusRequest("NOT_A_STATUS", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
        verify(statusHistoryRepository, never()).saveAndFlush(any(ListingStatusHistory.class));
    }

    @Test
    void rejectsATargetStatusTheSellerCannotDriveDirectly() {
        Listing listing = listing();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.transition(
                        55L, 1001L, new TransitionProductStatusRequest("PAID", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
        verify(statusHistoryRepository, never()).saveAndFlush(any(ListingStatusHistory.class));
    }

    @Test
    void letsSellerHideAnOnSaleListing() {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ON_SALE);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(statusHistoryRepository.saveAndFlush(any(ListingStatusHistory.class)))
                .thenAnswer(invocation -> {
                    ListingStatusHistory history = invocation.getArgument(0);
                    ReflectionTestUtils.setField(history, "id", 7003L);
                    return history;
                });

        var result = service.transition(
                55L, 1001L, new TransitionProductStatusRequest("HIDDEN", "잠시 내려둡니다"));

        assertThat(result.getPreviousStatus()).isEqualTo("ON_SALE");
        assertThat(result.getCurrentStatus()).isEqualTo("HIDDEN");
        assertThat(result.getReason()).isEqualTo("잠시 내려둡니다");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
    }

    // 목록 필터는 Specification 람다 안에서 조립된다. 저장소에 넘어간 Specification을 그대로
    // 잡아 criteria 목 위에서 평가해, 필터 조합마다 서로 다른 술어 분기를 타는지 확인한다.
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void buildsPublicSearchPredicatesForEveryFilterCombination() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findPublic(
                "갤럭시", 1L, 9L, null, BigDecimal.valueOf(100_000), BigDecimal.valueOf(900_000),
                "서울", "COMPLETED", 1, 5, 55L, 0, 20, null);
        service.findPublic(
                "   ", null, null, 101L, BigDecimal.valueOf(100_000), null, "   ", "in_progress",
                null, null, null, 0, 20, null);
        service.findPublic(
                null, null, null, null, null, BigDecimal.valueOf(900_000), null, "   ", 0, null,
                null, 0, 20, null);
        service.findPublic(
                null, null, null, null, null, null, null, null, null, 5, null, 0, 20, null);

        ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);
        verify(listingRepository, times(4)).findAll(captor.capture(), any(Pageable.class));
        for (Specification specification : captor.getAllValues()) {
            Root<Listing> root = mock(Root.class, RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
            CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
            assertThat(specification.toPredicate(root, query, builder)).isNotNull();
        }
    }

    private UpdateProductRequest updateRequestWithPrice(BigDecimal price) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setPrice(price);
        return request;
    }

    @Test
    void rejectsCreateForAModelThatIsUnknownOrDeactivated() {
        Category deactivated = model();
        deactivated.deactivate();
        when(categoryRepository.findById(101L))
                .thenReturn(Optional.empty(), Optional.of(deactivated));

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> service.create(55L, new CreateProductRequest(
                            1L, 101L, "Galaxy S24", null, BigDecimal.valueOf(650000),
                            "Black", 256, "서울 강남구")))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        }
        verify(listingRepository, never()).saveAndFlush(any(Listing.class));
    }

    /** 상위 카테고리를 그대로 고른 경우에도 요청한 카테고리와 어긋나면 등록을 막아야 한다. */
    @Test
    void rejectsCreateWhenATopLevelCategoryIsUsedAsAModelOfAnotherCategory() {
        Category topLevel = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(topLevel, "id", 1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(topLevel));

        assertThatThrownBy(() -> service.create(55L, new CreateProductRequest(
                        9L, 1L, "Galaxy S24", null, BigDecimal.valueOf(650000),
                        "Black", 256, "서울 강남구")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    @Test
    void rejectsCreateWhenTheModelHasNoPublishedChecklistTemplate() {
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model()));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(55L, new CreateProductRequest(
                        1L, 101L, "Galaxy S24", null, BigDecimal.valueOf(650000),
                        "Black", 256, "서울 강남구")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
        verify(listingRepository, never()).saveAndFlush(any(Listing.class));
    }

    @Test
    void rejectsAGeneratedChecklistThatDoesNotMatchTheSelectedModel() {
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model()));
        GeneratedChecklist forAnotherModel = new GeneratedChecklist(
                999L, "Samsung", "Galaxy S24", OsFamily.ANDROID, 1, false,
                List.of(generatedItem("PHN-FTR-FP", "FINGERPRINT")), List.of(), List.of());
        GeneratedChecklist withoutItems = new GeneratedChecklist(
                101L, "Samsung", "Galaxy S24", OsFamily.ANDROID, 1, false,
                List.of(), List.of(), List.of());

        for (GeneratedChecklist invalid : List.of(forAnotherModel, withoutItems)) {
            assertThatThrownBy(() -> service.create(
                            55L,
                            new CreateProductRequest(
                                    1L, 101L, "Galaxy S24", null, BigDecimal.valueOf(650000),
                                    "Black", 256, "서울 강남구"),
                            invalid))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
        verify(templateRepository, never()).saveAndFlush(any(ChecklistTemplate.class));
    }

    /** '기타 (직접 입력)' 자리표시자 대신 판매자가 적은 값이 등록 시점 스냅샷에 남아야 한다. */
    @Test
    void freezesSellerEnteredModelIntoTheCreatedSpecSnapshot() {
        Category model = model();
        ChecklistTemplate template = template();
        ChecklistTemplateItem required = ChecklistTemplateItem.create(
                template, "EXT-01", "외관", "외관 확인", "전체 촬영", EvidenceType.PHOTO,
                AutomationType.NONE, true, 1);
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(model));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(template));
        when(templateItemRepository.findByChecklistTemplateIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(required));
        when(listingRepository.saveAndFlush(any(Listing.class))).thenAnswer(invocation -> {
            Listing listing = invocation.getArgument(0);
            ReflectionTestUtils.setField(listing, "id", 1001L);
            return listing;
        });

        service.create(
                55L,
                new CreateProductRequest(
                        1L,
                        101L,
                        "gram 17",
                        "직접 입력 모델",
                        BigDecimal.valueOf(1_500_000),
                        "Black",
                        512,
                        "서울 송파구",
                        Set.of(),
                        "LG Electronics",
                        "gram 17",
                        "17Z90R",
                        OsFamily.WINDOWS));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCustomManufacturer()).isEqualTo("LG Electronics");
        assertThat(captor.getValue().getCustomModelName()).isEqualTo("gram 17");
        assertThat(captor.getValue().getSpecSnapshot())
                .contains("LG Electronics")
                .contains("gram 17")
                .doesNotContain("Galaxy S24");
    }

    @Test
    void updateBlocksRemovalWhenReinspectionRequestReferencesItem() {
        Listing listing = listingWithDeviceModel();
        ListingChecklistItem existing = confirmedFeatureItem(7020L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of())))
                .thenReturn(Map.of());
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(existing));
        when(reinspectionRequestItemRepository.existsByListingChecklistItem_Id(7020L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(55L, 1001L, updateRequestWithFeatures(Set.of())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_ITEM_LOCKED_BY_EVIDENCE));
        verify(checklistItemRepository, never()).deleteAll(anyList());
    }

    @Test
    void updateBlocksRemovalWhenRtcSessionResultReferencesItem() {
        Listing listing = listingWithDeviceModel();
        ListingChecklistItem existing = confirmedFeatureItem(7021L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(eq(101L), eq(Set.of())))
                .thenReturn(Map.of());
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(existing));
        when(rtcSessionChecklistResultRepository.existsByListingChecklistItemId(7021L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(55L, 1001L, updateRequestWithFeatures(Set.of())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_ITEM_LOCKED_BY_EVIDENCE));
        verify(checklistItemRepository, never()).deleteAll(anyList());
    }

    @Test
    void rejectsFeatureAdditionWhenTheListingTemplateIsGone() {
        Listing listing = listingWithDeviceModel();
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(
                        eq(101L), eq(Set.of("FINGERPRINT"))))
                .thenReturn(Map.of("FINGERPRINT", generatedItem("PHN-FTR-FP", "FINGERPRINT")));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of());
        when(templateRepository.findById(501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        55L, 1001L, updateRequestWithFeatures(Set.of("FINGERPRINT"))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
        verify(checklistItemRepository, never()).saveAll(anyList());
    }

    /** 매물 전용이어야 할 DRAFT 템플릿을 다른 매물이 함께 쓰고 있으면 조용히 고쳐선 안 된다. */
    @Test
    void failsWhenAListingOnlyDraftTemplateTurnsOutToBeShared() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplate draftTemplate = ChecklistTemplate.createDraft(101L, 3);
        ReflectionTestUtils.setField(draftTemplate, "id", 501L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(
                        eq(101L), eq(Set.of("FINGERPRINT"))))
                .thenReturn(Map.of("FINGERPRINT", generatedItem("PHN-FTR-FP", "FINGERPRINT")));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of());
        when(templateRepository.findById(501L)).thenReturn(Optional.of(draftTemplate));
        when(listingRepository.countByChecklistTemplateId(501L)).thenReturn(2L);

        assertThatThrownBy(() -> service.update(
                        55L, 1001L, updateRequestWithFeatures(Set.of("FINGERPRINT"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpectedly shared");
        verify(checklistItemRepository, never()).saveAll(anyList());
    }

    /** 껐다 켠 기능은 템플릿에 정의가 남아 있다 — 다시 만들면 (template, item_code) 제약을 깬다. */
    @Test
    void reusesTheExistingTemplateItemWhenAFeatureIsTurnedBackOn() {
        Listing listing = listingWithDeviceModel();
        ChecklistTemplate draftTemplate = ChecklistTemplate.createDraft(101L, 3);
        ReflectionTestUtils.setField(draftTemplate, "id", 501L);
        ChecklistTemplateItem leftoverDefinition = ChecklistTemplateItem.create(
                draftTemplate, "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 4);
        ReflectionTestUtils.setField(leftoverDefinition, "id", 9020L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNullForUpdate(1001L, 55L))
                .thenReturn(Optional.of(listing));
        when(checklistGenerationService.resolveConfirmedFeatureItems(
                        eq(101L), eq(Set.of("FINGERPRINT"))))
                .thenReturn(Map.of("FINGERPRINT", generatedItem("PHN-FTR-FP", "FINGERPRINT")));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of());
        when(templateRepository.findById(501L)).thenReturn(Optional.of(draftTemplate));
        when(listingRepository.countByChecklistTemplateId(501L)).thenReturn(1L);
        when(templateItemRepository.findByChecklistTemplateIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(leftoverDefinition));

        service.update(55L, 1001L, updateRequestWithFeatures(Set.of("FINGERPRINT")));

        verify(templateItemRepository, never()).saveAllAndFlush(anyList());
        verify(checklistItemRepository).saveAll(argThat((List<ListingChecklistItem> saved) ->
                saved.size() == 1 && saved.get(0).getItemCode().equals("PHN-FTR-FP")));
    }

    /** CDN URL이 비어 있어도 저장소 키로 서명 URL을 만들어 대표 이미지를 보여 줘야 한다. */
    @Test
    void resolvesThumbnailThroughTheMediaUrlResolverWhenConfigured() {
        MediaUrlResolver mediaUrlResolver = mock(MediaUrlResolver.class);
        ProductApplicationService withResolver = new ProductApplicationService(
                listingRepository, categoryRepository, templateRepository, templateItemRepository,
                checklistItemRepository, statusHistoryRepository, imageRepository,
                mediaUrlResolver, checklistGenerationService, evidenceRepository,
                reinspectionRequestItemRepository, rtcSessionChecklistResultRepository,
                mediaUploadSessionRepository, inspectionSessionTestResultRepository,
                viewCountDispatcher, engagementReader);
        ListingThumbnailProjection thumbnail = mock(ListingThumbnailProjection.class);
        when(thumbnail.getListingId()).thenReturn(1001L);
        when(thumbnail.getS3Key()).thenReturn("listing/1001/thumb.jpg");
        when(listingRepository.findBySellerIdAndDeletedAtIsNull(eq(55L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing())));
        when(imageRepository.findFirstByListingIdsAndImageType(
                        eq(List.of(1001L)),
                        eq(com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL)))
                .thenReturn(List.of(thumbnail));
        when(mediaUrlResolver.resolve("listing/1001/thumb.jpg", null))
                .thenReturn("https://cdn.example.com/signed/1001.jpg");

        var result = withResolver.findMine(55L, null, 0, 20, null);

        assertThat(result.content().get(0).getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/signed/1001.jpg");
    }

    @Test
    void flagsSellerListItemWhenPrivacyConfirmationIsStillPending() {
        ListingChecklistCountProjection count = mock(ListingChecklistCountProjection.class);
        when(count.getListingId()).thenReturn(1001L);
        when(count.getRequiredCount()).thenReturn(2L);
        when(count.getCompletedRequiredCount()).thenReturn(1L);
        when(count.getRequiredConfirmationCount()).thenReturn(1L);
        when(count.getCompletedConfirmationCount()).thenReturn(0L);
        when(listingRepository.findBySellerIdAndDeletedAtIsNull(eq(55L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing())));
        when(checklistItemRepository.countRequiredByListingIds(
                        eq(List.of(1001L)),
                        eq(ChecklistItemCompletionStatus.COMPLETED),
                        eq(EvidenceType.SELLER_CONFIRMATION)))
                .thenReturn(List.of(count));

        var result = service.findMine(55L, null, 0, 20, null);

        var item = result.content().get(0);
        assertThat(item.isPendingPrivacyConfirmation()).isTrue();
        assertThat(item.getRequiredItemCount()).isEqualTo(2);
        assertThat(item.getCompletedItemCount()).isEqualTo(1);
        assertThat(item.getThumbnailUrl()).isNull();
    }

    /** 직접 입력 모델은 오타를 고칠 수 있어야 한다. 보내지 않은 쪽은 기존 값을 유지한다. */
    @Test
    void updateKeepsTheStoredCustomModelFieldThatWasNotSent() {
        Listing listing = listing();
        listing.applyCustomModel("Samsng", "Galxy Book");
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));
        UpdateProductRequest manufacturerOnly = new UpdateProductRequest();
        manufacturerOnly.setCustomManufacturer("Samsung");
        UpdateProductRequest modelNameOnly = new UpdateProductRequest();
        modelNameOnly.setCustomModelName("Galaxy Book4");

        service.update(55L, 1001L, manufacturerOnly);

        assertThat(listing.getCustomManufacturer()).isEqualTo("Samsung");
        assertThat(listing.getCustomModelName()).isEqualTo("Galxy Book");

        service.update(55L, 1001L, modelNameOnly);

        assertThat(listing.getCustomManufacturer()).isEqualTo("Samsung");
        assertThat(listing.getCustomModelName()).isEqualTo("Galaxy Book4");
    }

    /** 리프가 아닌 카테고리를 그대로 모델로 쓴 매물도 상세를 만들 수 있어야 한다. */
    @Test
    void ownedDetailFallsBackToTheModelItselfWhenItHasNoParentCategory() {
        Category topLevel = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(topLevel, "id", 1L);
        Listing listing = Listing.createDraft(
                55L, topLevel, "Galaxy S24", "상태 양호", 650000, "Black", 256, "서울 강남구", 501L);
        ReflectionTestUtils.setField(listing, "id", 1001L);
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 55L))
                .thenReturn(Optional.of(listing));

        var result = service.findOwnedDetail(55L, 1001L);

        assertThat(result.getCategory().getCategoryId()).isEqualTo(1L);
        assertThat(result.getCategory().getParentId()).isNull();
        assertThat(result.getDevice().getOs()).isNull();
        assertThat(result.getThumbnailUrl()).isNull();
    }

    @Test
    void marksPublicSummaryInProgressWhileRequiredItemsRemainAndFallsBackToTheDefaultSort() {
        ListingChecklistCountProjection count = mock(ListingChecklistCountProjection.class);
        when(count.getListingId()).thenReturn(1001L);
        when(count.getRequiredCount()).thenReturn(3L);
        when(count.getCompletedRequiredCount()).thenReturn(1L);
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing())));
        when(checklistItemRepository.countRequiredByListingIds(
                        eq(List.of(1001L)),
                        eq(ChecklistItemCompletionStatus.COMPLETED),
                        eq(EvidenceType.SELLER_CONFIRMATION)))
                .thenReturn(List.of(count));

        var result = service.findPublic(
                null, null, null, null, null, null, null, null, null, null, null, 0, 20, "   ");

        assertThat(result.content().get(0).getVerificationStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.content().get(0).getCompletedItemCount()).isEqualTo(1);
        assertThat(result.content().get(0).getRequiredItemCount()).isEqualTo(3);
        verify(listingRepository).findAll(
                any(Specification.class),
                argThat((Pageable pageable) ->
                        pageable.getSort().getOrderFor("createdAt").isDescending()));
    }

    private ListingChecklistItem confirmedFeatureItem(Long id) {
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                template(), "PHN-FTR-FP", "지문 인식", "확인", "가이드", EvidenceType.VIDEO,
                AutomationType.NONE, true, 2);
        ListingChecklistItem item = ListingChecklistItem.createConfirmedFeatureItem(
                1001L, templateItem, "FINGERPRINT");
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
