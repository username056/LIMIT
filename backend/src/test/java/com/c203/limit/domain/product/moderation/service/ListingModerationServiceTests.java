package com.c203.limit.domain.product.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.moderation.dto.request.AdminModerationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.AdminRestorationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.CreateListingReportRequest;
import com.c203.limit.domain.product.moderation.dto.request.CreateRestorationRequest;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import com.c203.limit.domain.product.moderation.entity.ListingReport;
import com.c203.limit.domain.product.moderation.entity.ListingReportCategory;
import com.c203.limit.domain.product.moderation.entity.ListingReportStatus;
import com.c203.limit.domain.product.moderation.entity.ListingRestorationRequest;
import com.c203.limit.domain.product.moderation.entity.ModerationDecision;
import com.c203.limit.domain.product.moderation.entity.RestorationDecision;
import com.c203.limit.domain.product.moderation.repository.ListingReportRepository;
import com.c203.limit.domain.product.moderation.repository.ListingRestorationRequestRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ListingModerationServiceTests {
    private static final Long PRODUCT_ID = 1001L;
    private static final Long SELLER_ID = 55L;
    private static final Long REPORTER_ID = 77L;
    private static final Long ADMIN_ID = 9L;

    @Mock ListingRepository listingRepository;
    @Mock ListingReportRepository reportRepository;
    @Mock ListingRestorationRequestRepository restorationRepository;
    @Mock AdminActionLogRepository actionLogRepository;

    ListingModerationService service;

    @BeforeEach
    void setUp() {
        service = new ListingModerationService(
                listingRepository,
                reportRepository,
                restorationRepository,
                actionLogRepository,
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void loggedInMemberCanReportPublicProduct() {
        Listing listing = onSaleListing();
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(listing));
        when(reportRepository.existsByListingIdAndReporterId(PRODUCT_ID, REPORTER_ID))
                .thenReturn(false);
        when(reportRepository.saveAndFlush(any(ListingReport.class)))
                .thenAnswer(invocation -> {
                    ListingReport report = invocation.getArgument(0);
                    ReflectionTestUtils.setField(report, "id", 501L);
                    return report;
                });

        var response = service.report(
                REPORTER_ID,
                PRODUCT_ID,
                new CreateListingReportRequest(
                        ListingReportCategory.INACCURATE_INFORMATION,
                        "저장 용량 설명이 실제 상품과 다릅니다."));

        assertThat(response.reportId()).isEqualTo(501L);
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void sellerCannotReportOwnProduct() {
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(onSaleListing()));

        assertThatThrownBy(() -> service.report(
                        SELLER_ID,
                        PRODUCT_ID,
                        new CreateListingReportRequest(
                                ListingReportCategory.OTHER,
                                "본인 상품 신고를 시도합니다.")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SELF_REPORT_NOT_ALLOWED));
    }

    @Test
    void adminSuspensionImmediatelyRemovesProductFromPublicVisibility() {
        Listing listing = onSaleListing();
        ListingReport report = report();
        when(reportRepository.findById(501L)).thenReturn(Optional.of(report));
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(listing));

        var response = service.decideReport(
                ADMIN_ID,
                501L,
                new AdminModerationDecisionRequest(
                        ModerationDecision.SUSPEND,
                        "같은 상품을 반복 등록해 판매를 중지합니다."));

        assertThat(response.moderationStatus()).isEqualTo("SUSPENDED");
        assertThat(listing.isPubliclyVisible()).isFalse();
        assertThat(report.getStatus()).isEqualTo(ListingReportStatus.SUSPENDED);
        verify(actionLogRepository).save(any());
    }

    @Test
    void warningBecomesNormalOnlyAfterSellerAcknowledgement() {
        Listing listing = onSaleListing();
        ListingReport report = report();
        report.decide(
                ModerationDecision.WARN,
                ADMIN_ID,
                "상품 설명을 확인해 주세요.",
                java.time.LocalDateTime.of(2026, 8, 6, 9, 0));
        listing.issueModerationWarning("상품 설명을 확인해 주세요.");
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(reportRepository.findByListingIdAndStatusOrderByCreatedAtAsc(
                        PRODUCT_ID, ListingReportStatus.WARNING_ISSUED))
                .thenReturn(List.of(report));

        service.acknowledgeWarning(SELLER_ID, PRODUCT_ID);

        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.NORMAL);
        assertThat(report.getStatus()).isEqualTo(ListingReportStatus.RESOLVED);
    }

    @Test
    void restorationNeedsAdminApprovalBeforeProductReturnsToPublic() {
        Listing listing = onSaleListing();
        listing.suspendForModeration("사진을 수정해 주세요.");
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(restorationRepository.existsByListingIdAndStatus(any(), any())).thenReturn(false);
        when(restorationRepository.saveAndFlush(any(ListingRestorationRequest.class)))
                .thenAnswer(invocation -> {
                    ListingRestorationRequest request = invocation.getArgument(0);
                    ReflectionTestUtils.setField(request, "id", 601L);
                    return request;
                });

        service.requestRestoration(
                SELLER_ID, PRODUCT_ID, new CreateRestorationRequest("상품 사진을 교체했습니다."));

        assertThat(listing.getModerationStatus())
                .isEqualTo(ListingModerationStatus.RESTORE_REQUESTED);
        assertThat(listing.isPubliclyVisible()).isFalse();

        ListingRestorationRequest request = ListingRestorationRequest.create(
                PRODUCT_ID, SELLER_ID, "상품 사진을 교체했습니다.");
        ReflectionTestUtils.setField(request, "id", 601L);
        when(restorationRepository.findById(601L)).thenReturn(Optional.of(request));
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(listing));

        service.decideRestoration(
                ADMIN_ID,
                601L,
                new AdminRestorationDecisionRequest(RestorationDecision.APPROVE, "수정 확인"));

        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.NORMAL);
        assertThat(listing.isPubliclyVisible()).isTrue();
    }

    private Listing onSaleListing() {
        Category category = Category.createTopLevel("노트북", DeviceType.LAPTOP, 0);
        Listing listing = Listing.createDraft(
                SELLER_ID, category, "테스트 노트북", "상태 양호", 500_000, 10L);
        ReflectionTestUtils.setField(listing, "id", PRODUCT_ID);
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ON_SALE);
        return listing;
    }

    private ListingReport report() {
        ListingReport report = ListingReport.create(
                PRODUCT_ID,
                REPORTER_ID,
                ListingReportCategory.DUPLICATE_LISTING,
                "같은 상품이 여러 번 등록되어 있습니다.");
        ReflectionTestUtils.setField(report, "id", 501L);
        return report;
    }
}
