package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.c203.limit.domain.rtc.repository.RtcSessionChecklistResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        // 12개 필수 기본 항목(충전 제외) + 카메라(스펙 확인용, 필수 아님) = 12
        assertThat(result.getRequiredItemCount()).isEqualTo(12);
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
                true, true, 1, featureCode, null, null, null);
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
}
