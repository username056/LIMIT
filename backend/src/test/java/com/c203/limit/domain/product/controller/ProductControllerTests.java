package com.c203.limit.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.checklist.ChecklistGenerationService;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklist;
import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductRequest;
import com.c203.limit.domain.product.dto.response.MyProductSummaryResponse;
import com.c203.limit.domain.product.dto.response.ProductCreatedResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.dto.response.ProductStatusTransitionResponse;
import com.c203.limit.domain.product.dto.response.ProductSummaryResponse;
import com.c203.limit.domain.product.dto.response.PurchaseConfirmationResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.service.ListingService;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductApplicationService.MyProductPage;
import com.c203.limit.domain.product.service.ProductApplicationService.ProductPage;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageMetaResponse;
import com.c203.limit.global.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductControllerTests {
    private static final Long MEMBER_ID = 20L;
    private static final Long PRODUCT_ID = 1001L;

    @Mock ProductApplicationService productService;
    @Mock ListingService listingService;
    @Mock CurrentUser currentUser;
    @Mock SellerStatusReader sellerStatusReader;
    @Mock ChecklistGenerationService checklistGenerationService;
    ProductController controller;

    @BeforeEach
    void setUp() {
        controller =
                new ProductController(
                        productService,
                        listingService,
                        currentUser,
                        sellerStatusReader,
                        checklistGenerationService);
    }

    @Test
    void createsProductWithCatalogChecklistSnapshot() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        CreateProductRequest request = catalogRequest();
        GeneratedChecklist checklist = checklist();
        when(checklistGenerationService.generateSnapshotForModel(101L, Set.of()))
                .thenReturn(Optional.of(checklist));
        ProductCreatedResponse response = mock(ProductCreatedResponse.class);
        when(productService.create(MEMBER_ID, request, checklist)).thenReturn(response);

        var result = controller.createProduct(request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().data()).isSameAs(response);
        verify(sellerStatusReader).requireActiveSeller(MEMBER_ID);
    }

    @Test
    void createsProductWithoutChecklistWhenSnapshotIsUnavailable() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        CreateProductRequest request = catalogRequest();
        when(checklistGenerationService.generateSnapshotForModel(101L, Set.of()))
                .thenReturn(Optional.empty());
        ProductCreatedResponse response = mock(ProductCreatedResponse.class);
        when(productService.create(MEMBER_ID, request, null)).thenReturn(response);

        var result = controller.createProduct(request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        verify(productService).create(MEMBER_ID, request, null);
    }

    @Test
    void generatesCustomChecklistWhenSellerTypedModelManually() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        CreateProductRequest request = customModelRequest();
        GeneratedChecklist checklist = checklist();
        when(checklistGenerationService.generateCustomForModel(
                        101L,
                        "Samsung",
                        "Galaxy Book4 Pro",
                        "NT960XGK",
                        OsFamily.WINDOWS,
                        Set.of("BATTERY")))
                .thenReturn(checklist);
        ProductCreatedResponse response = mock(ProductCreatedResponse.class);
        when(productService.create(MEMBER_ID, request, checklist)).thenReturn(response);

        var result = controller.createProduct(request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        verify(checklistGenerationService, org.mockito.Mockito.never())
                .generateSnapshotForModel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsProductCreationWhenSellerProfileIsNotActive() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        doThrow(new BusinessException(ErrorCode.SELLER_NOT_ACTIVE))
                .when(sellerStatusReader)
                .requireActiveSeller(MEMBER_ID);

        assertThatThrownBy(() -> controller.createProduct(catalogRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SELLER_NOT_ACTIVE));
        verifyNoInteractions(productService, checklistGenerationService);
    }

    @Test
    void updatesOwnedProduct() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        UpdateProductRequest request = new UpdateProductRequest();
        ProductDetailResponse response = mock(ProductDetailResponse.class);
        when(productService.update(MEMBER_ID, PRODUCT_ID, request)).thenReturn(response);

        var result = controller.updateProduct(PRODUCT_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).isSameAs(response);
    }

    @Test
    void deletesOwnedProductWithoutContent() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);

        var result = controller.deleteProduct(PRODUCT_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
        verify(productService).delete(MEMBER_ID, PRODUCT_ID);
    }

    @Test
    void returnsPublicProductsWithPagingMeta() {
        ProductSummaryResponse summary = mock(ProductSummaryResponse.class);
        when(productService.findPublic(
                        "galaxy",
                        1L,
                        2L,
                        101L,
                        BigDecimal.valueOf(100000),
                        BigDecimal.valueOf(900000),
                        "서울 강남구",
                        "VERIFIED",
                        1,
                        5,
                        55L,
                        0,
                        20,
                        "createdAt,desc"))
                .thenReturn(new ProductPage(List.of(summary), 0, 20, 42L, 3, true));

        var result =
                controller.getProducts(
                        "galaxy",
                        1L,
                        2L,
                        101L,
                        BigDecimal.valueOf(100000),
                        BigDecimal.valueOf(900000),
                        "서울 강남구",
                        "VERIFIED",
                        1,
                        5,
                        55L,
                        0,
                        20,
                        "createdAt,desc");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).containsExactly(summary);
        assertThat(result.getBody().meta())
                .isEqualTo(new PageMetaResponse(0, 20, 42L, 3, true));
        verifyNoInteractions(currentUser);
    }

    @Test
    void returnsPublicProductDetailWithoutAuthentication() {
        ProductDetailResponse response = mock(ProductDetailResponse.class);
        when(productService.findPublicDetail(PRODUCT_ID)).thenReturn(response);

        var result = controller.getProduct(PRODUCT_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).isSameAs(response);
        verifyNoInteractions(currentUser, sellerStatusReader);
    }

    @Test
    void returnsOwnedProductDetailForCurrentSeller() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        ProductDetailResponse response = mock(ProductDetailResponse.class);
        when(productService.findOwnedDetail(MEMBER_ID, PRODUCT_ID)).thenReturn(response);

        var result = controller.getMyProduct(PRODUCT_ID);

        assertThat(result.getBody().data()).isSameAs(response);
        verify(sellerStatusReader).requireActiveSeller(MEMBER_ID);
    }

    @Test
    void returnsOwnedProductsWithPagingMeta() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        MyProductSummaryResponse summary = mock(MyProductSummaryResponse.class);
        when(productService.findMine(MEMBER_ID, "DRAFT", 1, 10, "createdAt,asc"))
                .thenReturn(new MyProductPage(List.of(summary), 1, 10, 11L, 2, false));

        var result = controller.getMyProducts("DRAFT", 1, 10, "createdAt,asc");

        assertThat(result.getBody().data()).containsExactly(summary);
        assertThat(result.getBody().meta())
                .isEqualTo(new PageMetaResponse(1, 10, 11L, 2, false));
    }

    @Test
    void returnsCreatedStatusWhenProductStatusTransitionRecorded() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        TransitionProductStatusRequest request =
                new TransitionProductStatusRequest("ON_SALE", "필수 체크리스트 완료");
        ProductStatusTransitionResponse response = mock(ProductStatusTransitionResponse.class);
        when(productService.transition(MEMBER_ID, PRODUCT_ID, request)).thenReturn(response);

        var result = controller.transitionProductStatus(PRODUCT_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().data()).isSameAs(response);
    }

    @Test
    void confirmsPurchaseAndConvertsConfirmedAtToSeoulOffset() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        Listing listing = mock(Listing.class);
        when(listing.getId()).thenReturn(PRODUCT_ID);
        when(listing.getStatus()).thenReturn(ListingStatus.CONFIRMED);
        when(listing.getConfirmedAt()).thenReturn(LocalDateTime.of(2026, 7, 22, 12, 0));
        when(listingService.confirmByBuyer(PRODUCT_ID, MEMBER_ID)).thenReturn(listing);

        var result = controller.confirmPurchase(PRODUCT_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        PurchaseConfirmationResponse body = result.getBody().data();
        assertThat(body.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(body.getStatus()).isEqualTo("CONFIRMED");
        assertThat(body.getConfirmedAt())
                .isEqualTo(
                        OffsetDateTime.of(
                                2026, 7, 22, 12, 0, 0, 0, ZoneOffset.ofHours(9)));
        verifyNoInteractions(sellerStatusReader);
    }

    @Test
    void confirmsPurchaseWithoutConfirmedAtWhenListingHasNoTimestamp() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        Listing listing = mock(Listing.class);
        when(listing.getId()).thenReturn(PRODUCT_ID);
        when(listing.getStatus()).thenReturn(ListingStatus.PAID);
        when(listing.getConfirmedAt()).thenReturn(null);
        when(listingService.confirmByBuyer(PRODUCT_ID, MEMBER_ID)).thenReturn(listing);

        var result = controller.confirmPurchase(PRODUCT_ID);

        assertThat(result.getBody().data().getConfirmedAt()).isNull();
        assertThat(result.getBody().data().getStatus()).isEqualTo("PAID");
    }

    private CreateProductRequest catalogRequest() {
        return new CreateProductRequest(
                1L,
                101L,
                "Galaxy S24 256GB",
                "생활 흠집이 있습니다.",
                BigDecimal.valueOf(650000),
                "Onyx Black",
                256,
                "서울 강남구");
    }

    private CreateProductRequest customModelRequest() {
        return new CreateProductRequest(
                1L,
                101L,
                "Galaxy Book4 Pro",
                "직접 입력 모델",
                BigDecimal.valueOf(1500000),
                "Moonstone Gray",
                512,
                "서울 송파구",
                Set.of("BATTERY"),
                "Samsung",
                "Galaxy Book4 Pro",
                "NT960XGK",
                OsFamily.WINDOWS);
    }

    private GeneratedChecklist checklist() {
        return new GeneratedChecklist(
                101L,
                "Samsung",
                "Galaxy S24",
                OsFamily.ANDROID,
                1,
                false,
                List.of(),
                List.of(),
                List.of());
    }
}
