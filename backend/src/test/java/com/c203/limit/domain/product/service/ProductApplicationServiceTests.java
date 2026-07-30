package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.repository.ListingChecklistCountProjection;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklist;
import com.c203.limit.domain.inspection.checklist.LaptopChecklistPolicy;
import com.c203.limit.domain.inspection.checklist.LaptopFeatureCode;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
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
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
    ProductApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProductApplicationService(
                listingRepository, categoryRepository, templateRepository, templateItemRepository,
                checklistItemRepository, statusHistoryRepository, imageRepository);
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
        assertThat(result.getRequiredItemCount()).isEqualTo(13);
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

    @Test
    void loadsChecklistCountsAndThumbnailsOnceForAProductPage() {
        Listing first = listing();
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
                        eq(List.of(1001L, 1002L)), eq(ChecklistItemCompletionStatus.COMPLETED)))
                .thenReturn(List.of(firstCount));
        ListingThumbnailProjection thumbnail = mock(ListingThumbnailProjection.class);
        when(thumbnail.getListingId()).thenReturn(1001L);
        when(thumbnail.getCdnUrl()).thenReturn("https://cdn.example.com/1001.jpg");
        when(imageRepository.findFirstByListingIdsAndImageType(
                        eq(List.of(1001L, 1002L)), eq(com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL)))
                .thenReturn(List.of(thumbnail));

        var result = service.findPublic(
                null, null, null, null, null, null, null, null, 0, 20, "price,asc");

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).getVerificationStatus()).isEqualTo("COMPLETED");
        assertThat(result.content().get(0).getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/1001.jpg");
        verify(checklistItemRepository).countRequiredByListingIds(
                List.of(1001L, 1002L), ChecklistItemCompletionStatus.COMPLETED);
        verify(imageRepository).findFirstByListingIdsAndImageType(
                List.of(1001L, 1002L),
                com.c203.limit.domain.product.entity.ListingImageType.THUMBNAIL);
        verify(listingRepository).findAll(
                any(Specification.class),
                argThat((Pageable pageable) ->
                        pageable.getSort().getOrderFor("price").isAscending()));
    }

    @Test
    void rejectsUnsupportedVerificationStatusAndSort() {
        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, "UNKNOWN", 0, 20,
                        "createdAt,desc"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        assertThatThrownBy(() -> service.findPublic(
                        null, null, null, null, null, null, null, null, 0, 20,
                        "sellerId,asc"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
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
