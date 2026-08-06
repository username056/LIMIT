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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    void duplicateReportIsRejected() {
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(onSaleListing()));
        when(reportRepository.existsByListingIdAndReporterId(PRODUCT_ID, REPORTER_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.report(
                        REPORTER_ID,
                        PRODUCT_ID,
                        new CreateListingReportRequest(ListingReportCategory.OTHER, "duplicate")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS));
    }

    @Test
    void nonPublicProductCannotBeReported() {
        Listing draft = onSaleListing();
        ReflectionTestUtils.setField(draft, "status", ListingStatus.DRAFT);
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.report(
                        REPORTER_ID,
                        PRODUCT_ID,
                        new CreateListingReportRequest(ListingReportCategory.OTHER, "hidden")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
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
    void adminCanWarnOrDismissReport() {
        Listing warnedListing = onSaleListing();
        ListingReport warnedReport = report(501L);
        when(reportRepository.findById(501L)).thenReturn(Optional.of(warnedReport));
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(warnedListing));

        var warned = service.decideReport(
                ADMIN_ID,
                501L,
                new AdminModerationDecisionRequest(ModerationDecision.WARN, "please review"));

        assertThat(warned.status()).isEqualTo("WARNING_ISSUED");
        assertThat(warnedListing.getModerationStatus())
                .isEqualTo(ListingModerationStatus.WARNING_ACK_REQUIRED);

        Listing dismissedListing = onSaleListing();
        ListingReport dismissedReport = report(502L);
        when(reportRepository.findById(502L)).thenReturn(Optional.of(dismissedReport));
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(dismissedListing));

        var dismissed = service.decideReport(
                ADMIN_ID,
                502L,
                new AdminModerationDecisionRequest(ModerationDecision.DISMISS, "not a violation"));

        assertThat(dismissed.status()).isEqualTo("DISMISSED");
        assertThat(dismissedListing.getModerationStatus()).isEqualTo(ListingModerationStatus.NORMAL);
    }

    @Test
    void reportDecisionRejectsMissingReportOrProduct() {
        when(reportRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decideReport(
                        ADMIN_ID,
                        404L,
                        new AdminModerationDecisionRequest(ModerationDecision.DISMISS, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_NOT_FOUND));

        ListingReport report = report(501L);
        when(reportRepository.findById(501L)).thenReturn(Optional.of(report));
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decideReport(
                        ADMIN_ID,
                        501L,
                        new AdminModerationDecisionRequest(ModerationDecision.DISMISS, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
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
    void sellerNoticesExposeOnlyModerationInformation() {
        ListingReport warning = report(501L);
        warning.decide(
                ModerationDecision.WARN,
                ADMIN_ID,
                "review title",
                java.time.LocalDateTime.of(2026, 8, 6, 9, 0));
        ListingReport suspended = report(502L);
        suspended.decide(
                ModerationDecision.SUSPEND,
                ADMIN_ID,
                "remove image",
                java.time.LocalDateTime.of(2026, 8, 6, 10, 0));
        when(reportRepository.findByListingIdAndStatusInOrderByReviewedAtDesc(
                        PRODUCT_ID,
                        List.of(ListingReportStatus.WARNING_ISSUED, ListingReportStatus.SUSPENDED)))
                .thenReturn(List.of(suspended, warning));

        var notices = service.sellerNotices(PRODUCT_ID);

        assertThat(notices).extracting("reportId", "adminNote")
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(502L, "remove image"),
                        org.assertj.core.api.Assertions.tuple(501L, "review title"));
    }

    @Test
    void adminReportListIncludesProductWhenAvailableAndPreservesMissingProduct() {
        ListingReport first = report(501L);
        ListingReport second = ListingReport.create(
                2002L, REPORTER_ID, ListingReportCategory.FRAUD_SUSPECTED, "fraud");
        ReflectionTestUtils.setField(second, "id", 502L);
        when(reportRepository.findAll(
                        org.mockito.ArgumentMatchers.<Specification<ListingReport>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)), new PageImpl<>(List.of()));
        when(listingRepository.findAllById(any()))
                .thenReturn(List.of(onSaleListing()), List.of());

        var filtered = service.reports(ListingReportStatus.PENDING, 0, 20);
        var unfiltered = service.reports(null, 0, 20);

        assertThat(filtered.getContent()).hasSize(2);
        assertThat(filtered.getContent().get(0).productName()).isNotNull();
        assertThat(filtered.getContent().get(1).productName()).isNull();
        assertThat(filtered.getContent().get(1).sellerId()).isNull();
        assertThat(unfiltered.getContent()).isEmpty();
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

    @Test
    void duplicatePendingRestorationIsRejected() {
        Listing listing = onSaleListing();
        listing.suspendForModeration("fix required");
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(restorationRepository.existsByListingIdAndStatus(
                        PRODUCT_ID, com.c203.limit.domain.product.moderation.entity.RestorationRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requestRestoration(
                        SELLER_ID, PRODUCT_ID, new CreateRestorationRequest("fixed")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RESTORATION_PENDING_ALREADY_EXISTS));
    }

    @Test
    void adminCanRejectRestorationRequest() {
        Listing listing = onSaleListing();
        listing.suspendForModeration("fix required");
        listing.requestModerationRestoration();
        ListingRestorationRequest restoration = restoration(601L, PRODUCT_ID);
        when(restorationRepository.findById(601L)).thenReturn(Optional.of(restoration));
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.of(listing));

        var response = service.decideRestoration(
                ADMIN_ID,
                601L,
                new AdminRestorationDecisionRequest(RestorationDecision.REJECT, "still duplicated"));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.moderationStatus()).isEqualTo("SUSPENDED");
        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.SUSPENDED);
        verify(actionLogRepository).save(any());
    }

    @Test
    void restorationListIncludesProductWhenAvailableAndPreservesMissingProduct() {
        ListingRestorationRequest first = restoration(601L, PRODUCT_ID);
        ListingRestorationRequest second = restoration(602L, 2002L);
        when(restorationRepository.findAll(
                        org.mockito.ArgumentMatchers.<Specification<ListingRestorationRequest>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)), new PageImpl<>(List.of()));
        when(listingRepository.findAllById(any()))
                .thenReturn(List.of(onSaleListing()), List.of());

        var filtered = service.restorationRequests(
                com.c203.limit.domain.product.moderation.entity.RestorationRequestStatus.PENDING,
                0,
                20);
        var unfiltered = service.restorationRequests(null, 0, 20);

        assertThat(filtered.getContent()).hasSize(2);
        assertThat(filtered.getContent().get(0).productName()).isNotNull();
        assertThat(filtered.getContent().get(1).productName()).isNull();
        assertThat(filtered.getContent().get(1).moderationStatus()).isNull();
        assertThat(unfiltered.getContent()).isEmpty();
    }

    @Test
    void restorationDecisionRejectsMissingRequestOrProduct() {
        when(restorationRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decideRestoration(
                        ADMIN_ID,
                        404L,
                        new AdminRestorationDecisionRequest(RestorationDecision.REJECT, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RESTORATION_REQUEST_NOT_FOUND));

        ListingRestorationRequest restoration = restoration(601L, PRODUCT_ID);
        when(restorationRepository.findById(601L)).thenReturn(Optional.of(restoration));
        when(listingRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decideRestoration(
                        ADMIN_ID,
                        601L,
                        new AdminRestorationDecisionRequest(RestorationDecision.REJECT, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
    }

    @Test
    void sellerCommandsAndInvalidPagingAreRejected() {
        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acknowledgeWarning(SELLER_ID, PRODUCT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
        assertThatThrownBy(() -> service.reports(null, -1, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.restorationRequests(null, 0, 101))
                .isInstanceOf(BusinessException.class);
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
        return report(501L);
    }

    private ListingReport report(Long id) {
        ListingReport report = ListingReport.create(
                PRODUCT_ID,
                REPORTER_ID,
                ListingReportCategory.DUPLICATE_LISTING,
                "같은 상품이 여러 번 등록되어 있습니다.");
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    private ListingRestorationRequest restoration(Long id, Long productId) {
        ListingRestorationRequest restoration =
                ListingRestorationRequest.create(productId, SELLER_ID, "fixed product");
        ReflectionTestUtils.setField(restoration, "id", id);
        return restoration;
    }
}
