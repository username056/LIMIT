package com.c203.limit.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.dto.request.CompleteEvidenceRequest;
import com.c203.limit.domain.product.dto.request.CreateEvidenceUploadUrlRequest;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ProductChecklistItemResponse;
import com.c203.limit.domain.product.service.EvidenceDeleteService;
import com.c203.limit.domain.product.service.EvidenceHistoryService;
import com.c203.limit.domain.product.service.EvidenceUploadService;
import com.c203.limit.domain.product.service.ProductChecklistService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.CurrentUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductEvidenceControllerTests {
    private static final Long MEMBER_ID = 20L;
    private static final Long PRODUCT_ID = 1001L;
    private static final Long CHECKLIST_ITEM_ID = 7002L;
    private static final Long EVIDENCE_ID = 9001L;

    @Mock ProductChecklistService productChecklistService;
    @Mock EvidenceUploadService evidenceUploadService;
    @Mock EvidenceHistoryService evidenceHistoryService;
    @Mock EvidenceDeleteService evidenceDeleteService;
    @Mock CurrentUser currentUser;
    @Mock SellerStatusReader sellerStatusReader;
    ProductEvidenceController controller;

    @BeforeEach
    void setUp() {
        controller =
                new ProductEvidenceController(
                        productChecklistService,
                        evidenceUploadService,
                        evidenceHistoryService,
                        evidenceDeleteService,
                        currentUser,
                        sellerStatusReader);
    }

    @Test
    void returnsChecklistItemsWithoutAuthentication() {
        ProductChecklistItemResponse item = mock(ProductChecklistItemResponse.class);
        when(currentUser.memberIdOrNull()).thenReturn(null);
        when(productChecklistService.findAll(PRODUCT_ID, "PENDING", true, null))
                .thenReturn(List.of(item));

        var result = controller.getProductChecklist(PRODUCT_ID, "PENDING", true);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).containsExactly(item);
        verifyNoInteractions(sellerStatusReader);
    }

    @Test
    void returnsEmptyChecklistWhenNoItemMatchesTheFilter() {
        when(currentUser.memberIdOrNull()).thenReturn(null);
        when(productChecklistService.findAll(PRODUCT_ID, null, false, null)).thenReturn(List.of());

        var result = controller.getProductChecklist(PRODUCT_ID, null, false);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).isEmpty();
    }

    @Test
    void createsUploadUrlForAnActiveSeller() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        CreateEvidenceUploadUrlRequest request =
                new CreateEvidenceUploadUrlRequest("hinge.mp4", "video/mp4", 10_000L, 12);
        EvidenceUploadUrlResponse response = mock(EvidenceUploadUrlResponse.class);
        when(evidenceUploadService.createUploadUrl(
                        MEMBER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, request))
                .thenReturn(response);

        var result =
                controller.createEvidenceUploadUrl(PRODUCT_ID, CHECKLIST_ITEM_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().data()).isSameAs(response);
        verify(sellerStatusReader).requireActiveSeller(MEMBER_ID);
    }

    @Test
    void rejectsUploadUrlWhenSellerProfileIsNotActive() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        doThrow(new BusinessException(ErrorCode.SELLER_NOT_ACTIVE))
                .when(sellerStatusReader)
                .requireActiveSeller(MEMBER_ID);

        assertThatThrownBy(
                        () ->
                                controller.createEvidenceUploadUrl(
                                        PRODUCT_ID,
                                        CHECKLIST_ITEM_ID,
                                        new CreateEvidenceUploadUrlRequest(
                                                "hinge.mp4", "video/mp4", 10_000L, 12)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SELLER_NOT_ACTIVE));
        verifyNoInteractions(evidenceUploadService);
    }

    @Test
    void propagatesUploadUrlValidationFailureFromTheService() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        CreateEvidenceUploadUrlRequest request =
                new CreateEvidenceUploadUrlRequest("hinge.mp4", "video/mp4", 10_000L, 999);
        when(evidenceUploadService.createUploadUrl(
                        MEMBER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, request))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID));

        assertThatThrownBy(
                        () ->
                                controller.createEvidenceUploadUrl(
                                        PRODUCT_ID, CHECKLIST_ITEM_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_INVALID));
    }

    @Test
    void completesEvidenceForAnActiveSeller() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        CompleteEvidenceRequest request =
                new CompleteEvidenceRequest(
                        "upload-1", OffsetDateTime.parse("2026-07-29T17:00:00+09:00"));
        EvidenceResponse response = mock(EvidenceResponse.class);
        when(evidenceUploadService.complete(MEMBER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, request))
                .thenReturn(response);

        var result = controller.completeEvidence(PRODUCT_ID, CHECKLIST_ITEM_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().data()).isSameAs(response);
        verify(sellerStatusReader).requireActiveSeller(MEMBER_ID);
    }

    @Test
    void propagatesDuplicateCompletionFromTheService() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        CompleteEvidenceRequest request = new CompleteEvidenceRequest("upload-1", null);
        when(evidenceUploadService.complete(MEMBER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, request))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED));

        assertThatThrownBy(
                        () -> controller.completeEvidence(PRODUCT_ID, CHECKLIST_ITEM_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED));
    }

    @Test
    void returnsEvidenceHistoryForAnAnonymousViewer() {
        when(currentUser.memberIdOrNull()).thenReturn(null);
        when(evidenceHistoryService.findAll(PRODUCT_ID, CHECKLIST_ITEM_ID, null))
                .thenReturn(List.of());

        var result = controller.getEvidenceHistory(PRODUCT_ID, CHECKLIST_ITEM_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).isEmpty();
        verifyNoInteractions(sellerStatusReader);
    }

    @Test
    void returnsEvidenceHistoryForTheSignedInMember() {
        when(currentUser.memberIdOrNull()).thenReturn(MEMBER_ID);
        EvidenceResponse evidence = mock(EvidenceResponse.class);
        when(evidenceHistoryService.findAll(PRODUCT_ID, CHECKLIST_ITEM_ID, MEMBER_ID))
                .thenReturn(List.of(evidence));

        var result = controller.getEvidenceHistory(PRODUCT_ID, CHECKLIST_ITEM_ID);

        assertThat(result.getBody().data()).containsExactly(evidence);
        verify(evidenceHistoryService).findAll(PRODUCT_ID, CHECKLIST_ITEM_ID, MEMBER_ID);
    }

    @Test
    void deletesEvidenceWithoutContent() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);

        var result = controller.deleteEvidence(PRODUCT_ID, CHECKLIST_ITEM_ID, EVIDENCE_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
        verify(sellerStatusReader).requireActiveSeller(MEMBER_ID);
        verify(evidenceDeleteService)
                .delete(MEMBER_ID, PRODUCT_ID, CHECKLIST_ITEM_ID, EVIDENCE_ID);
    }

    @Test
    void rejectsEvidenceDeletionWhenSellerProfileIsNotActive() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        doThrow(new BusinessException(ErrorCode.SELLER_NOT_ACTIVE))
                .when(sellerStatusReader)
                .requireActiveSeller(MEMBER_ID);

        assertThatThrownBy(
                        () ->
                                controller.deleteEvidence(
                                        PRODUCT_ID, CHECKLIST_ITEM_ID, EVIDENCE_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SELLER_NOT_ACTIVE));
        verifyNoInteractions(evidenceDeleteService);
    }
}
