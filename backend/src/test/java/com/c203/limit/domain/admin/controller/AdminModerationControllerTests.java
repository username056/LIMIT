package com.c203.limit.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.moderation.dto.request.AdminModerationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.AdminRestorationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.ResolveRiskSignalRequest;
import com.c203.limit.domain.product.moderation.dto.response.AdminListingReportResponse;
import com.c203.limit.domain.product.moderation.dto.response.AdminModeratedProductResponse;
import com.c203.limit.domain.product.moderation.dto.response.AdminRestorationRequestResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationDashboardResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationRiskSignalResponse;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import com.c203.limit.domain.product.moderation.entity.ListingReportStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationDecision;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskType;
import com.c203.limit.domain.product.moderation.entity.RestorationRequestStatus;
import com.c203.limit.domain.product.moderation.entity.RestorationDecision;
import com.c203.limit.domain.product.moderation.service.ListingModerationService;
import com.c203.limit.domain.product.moderation.service.ModerationRiskService;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.global.response.PageResponse;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminModerationControllerTests {
    @Mock ListingModerationService moderationService;
    @Mock ModerationRiskService riskService;
    @Mock CurrentUser currentUser;
    @Mock ProductApplicationService productService;

    AdminModerationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminModerationController(
                moderationService, riskService, currentUser, productService);
    }

    @Test
    void adminIdentityIsPassedToReportDecision() {
        AdminModerationDecisionRequest request = new AdminModerationDecisionRequest(
                ModerationDecision.SUSPEND, "중복 등록을 확인했습니다.");
        AdminListingReportResponse response = org.mockito.Mockito.mock(
                AdminListingReportResponse.class);
        when(currentUser.adminId()).thenReturn(9L);
        when(moderationService.decideReport(9L, 501L, request)).thenReturn(response);

        var result = controller.decideReport(501L, request);

        verify(moderationService).decideReport(9L, 501L, request);
        assertThat(result.getBody().data()).isSameAs(response);
    }

    @Test
    void adminIdentityIsPassedToRestorationDecision() {
        AdminRestorationDecisionRequest request = new AdminRestorationDecisionRequest(
                RestorationDecision.APPROVE, "수정 사항을 확인했습니다.");
        AdminRestorationRequestResponse response = org.mockito.Mockito.mock(
                AdminRestorationRequestResponse.class);
        when(currentUser.adminId()).thenReturn(9L);
        when(moderationService.decideRestoration(9L, 601L, request)).thenReturn(response);

        var result = controller.decideRestoration(601L, request);

        verify(moderationService).decideRestoration(9L, 601L, request);
        assertThat(result.getBody().data()).isSameAs(response);
    }

    @Test
    void adminCanReadSuspendedProductThroughDedicatedEndpoint() {
        ProductDetailResponse response = new ProductDetailResponse(
                1001L,
                55L,
                null,
                null,
                "판매 중지 상품",
                "관리자 확인용",
                null,
                "ON_SALE",
                "SUSPENDED",
                "중복 등록",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                0L,
                0L,
                0L,
                false,
                List.of());
        when(productService.findAdminDetail(1001L)).thenReturn(response);

        var result = controller.productDetail(1001L);

        assertThat(result.getBody().data()).isSameAs(response);
    }

    @Test
    void adminCanReadEveryModerationQueueAndDashboard() {
        ModerationDashboardResponse dashboard =
                new ModerationDashboardResponse(1, 2, 3, 4, 5, List.of(), List.of());
        PageResponse<AdminListingReportResponse> reports = emptyPage();
        PageResponse<AdminRestorationRequestResponse> restorations = emptyPage();
        PageResponse<ModerationRiskSignalResponse> risks = emptyPage();
        PageResponse<AdminModeratedProductResponse> products = emptyPage();
        when(riskService.dashboard()).thenReturn(dashboard);
        when(moderationService.reports(ListingReportStatus.PENDING, 0, 20))
                .thenReturn(reports);
        when(moderationService.restorationRequests(RestorationRequestStatus.PENDING, 0, 20))
                .thenReturn(restorations);
        when(riskService.signals(
                        ModerationRiskStatus.OPEN,
                        ModerationRiskType.SIMILAR_IMAGE,
                        0,
                        20))
                .thenReturn(risks);
        when(riskService.products(
                        "gram",
                        55L,
                        ListingStatus.ON_SALE,
                        ListingModerationStatus.SUSPENDED,
                        0,
                        20))
                .thenReturn(products);

        assertThat(controller.dashboard().getBody().data()).isSameAs(dashboard);
        assertThat(controller.reports(ListingReportStatus.PENDING, 0, 20).getBody().data())
                .isSameAs(reports);
        assertThat(controller.restorationRequests(RestorationRequestStatus.PENDING, 0, 20)
                        .getBody()
                        .data())
                .isSameAs(restorations);
        assertThat(controller.riskSignals(
                                ModerationRiskStatus.OPEN,
                                ModerationRiskType.SIMILAR_IMAGE,
                                0,
                                20)
                        .getBody()
                        .data())
                .isSameAs(risks);
        assertThat(controller.products(
                                "gram",
                                55L,
                                ListingStatus.ON_SALE,
                                ListingModerationStatus.SUSPENDED,
                                0,
                                20)
                        .getBody()
                        .data())
                .isSameAs(products);
    }

    @Test
    void adminIdentityIsPassedToRiskResolution() {
        ResolveRiskSignalRequest request = new ResolveRiskSignalRequest("reviewed");
        ModerationRiskSignalResponse response = org.mockito.Mockito.mock(
                ModerationRiskSignalResponse.class);
        when(currentUser.adminId()).thenReturn(9L);
        when(riskService.resolve(9L, 701L, "reviewed")).thenReturn(response);

        var result = controller.resolveRiskSignal(701L, request);

        verify(riskService).resolve(9L, 701L, "reviewed");
        assertThat(result.getBody().data()).isSameAs(response);
    }

    private <T> PageResponse<T> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, false);
    }
}
