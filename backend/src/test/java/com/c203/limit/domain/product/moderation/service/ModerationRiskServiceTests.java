package com.c203.limit.domain.product.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImage;
import com.c203.limit.domain.product.entity.ListingImageType;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import com.c203.limit.domain.product.moderation.entity.ListingReportStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskSignal;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskType;
import com.c203.limit.domain.product.moderation.entity.RestorationRequestStatus;
import com.c203.limit.domain.product.moderation.repository.ListingReportRepository;
import com.c203.limit.domain.product.moderation.repository.ListingRestorationRequestRepository;
import com.c203.limit.domain.product.moderation.repository.ModerationRiskSignalRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.SellerPublishingCountProjection;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModerationRiskServiceTests {
    @Mock ListingRepository listingRepository;
    @Mock ListingStatusHistoryRepository historyRepository;
    @Mock ListingImageRepository imageRepository;
    @Mock ListingReportRepository reportRepository;
    @Mock ListingRestorationRequestRepository restorationRepository;
    @Mock ModerationRiskSignalRepository riskRepository;
    @Mock MemberRepository memberRepository;
    @Mock AdminActionLogRepository actionLogRepository;

    ModerationRiskService service;

    @BeforeEach
    void setUp() {
        service = new ModerationRiskService(
                listingRepository,
                historyRepository,
                imageRepository,
                reportRepository,
                restorationRepository,
                riskRepository,
                memberRepository,
                actionLogRepository,
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC),
                30,
                7,
                85,
                8);
    }

    @Test
    void thirtyFirstPublicationCreatesReviewSignalWithoutAutomaticSanction() {
        Listing listing = listing(1001L, "그램 노트북 판매");
        when(historyRepository.countFirstPublicationsBySellerSince(eq(55L), any()))
                .thenReturn(30L);
        when(riskRepository.findFirstBySellerIdAndTypeAndStatus(
                        55L, ModerationRiskType.PUBLISHING_VELOCITY, ModerationRiskStatus.OPEN))
                .thenReturn(Optional.empty());
        when(listingRepository.findBySellerIdAndIdNotAndDeletedAtIsNull(
                        eq(55L), eq(1001L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.analyzePublication(listing);

        ArgumentCaptor<ModerationRiskSignal> signalCaptor =
                ArgumentCaptor.forClass(ModerationRiskSignal.class);
        verify(riskRepository).save(signalCaptor.capture());
        assertThat(signalCaptor.getValue().getType())
                .isEqualTo(ModerationRiskType.PUBLISHING_VELOCITY);
        assertThat(signalCaptor.getValue().getStatus()).isEqualTo(ModerationRiskStatus.OPEN);
        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.NORMAL);
        assertThat(listing.isPubliclyVisible()).isTrue();
    }

    @Test
    void publicationRefreshesExistingVelocitySignalAndDetectsSimilarTitle() {
        Listing listing = listing(1001L, "LG Gram 16 2025");
        Listing similar = listing(900L, "LG-Gram 16 (2025)");
        ModerationRiskSignal velocity = signal(
                700L, 55L, 800L, null, ModerationRiskType.PUBLISHING_VELOCITY, 70);
        when(historyRepository.countFirstPublicationsBySellerSince(eq(55L), any()))
                .thenReturn(60L);
        when(riskRepository.findFirstBySellerIdAndTypeAndStatus(
                        55L, ModerationRiskType.PUBLISHING_VELOCITY, ModerationRiskStatus.OPEN))
                .thenReturn(Optional.of(velocity));
        when(listingRepository.findBySellerIdAndIdNotAndDeletedAtIsNull(
                        eq(55L), eq(1001L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(similar)));
        when(riskRepository.findByFingerprint("SIMILAR_TITLE:55:900:1001"))
                .thenReturn(Optional.empty());

        service.analyzePublication(listing);

        assertThat(velocity.getScore()).isEqualTo(100);
        ArgumentCaptor<ModerationRiskSignal> signalCaptor =
                ArgumentCaptor.forClass(ModerationRiskSignal.class);
        verify(riskRepository).save(signalCaptor.capture());
        assertThat(signalCaptor.getValue().getType()).isEqualTo(ModerationRiskType.SIMILAR_TITLE);
        assertThat(signalCaptor.getValue().getListingId()).isEqualTo(900L);
        assertThat(signalCaptor.getValue().getRelatedListingId()).isEqualTo(1001L);
    }

    @Test
    void publicationBelowThresholdIgnoresDifferentModelAndDissimilarTitles() {
        Listing listing = listing(1001L, "LG Gram 16");
        Listing differentModel = listing(1002L, "LG Gram 16");
        ReflectionTestUtils.setField(differentModel, "deviceModelId", 202L);
        Listing dissimilar = listing(1003L, "MacBook Pro");
        when(historyRepository.countFirstPublicationsBySellerSince(eq(55L), any()))
                .thenReturn(2L);
        when(listingRepository.findBySellerIdAndIdNotAndDeletedAtIsNull(
                        eq(55L), eq(1001L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(differentModel, dissimilar)));

        service.analyzePublication(listing);

        verify(riskRepository, never()).save(any());
    }

    @Test
    void exactDuplicateImageCreatesMaximumRiskSignalAndSkipsSameListing() {
        Listing listing = listing(1001L, "Current");
        Listing related = listing(900L, "Related");
        ListingImage image = image(101L, listing, "same-sha", "0000000000000000");
        ListingImage sameListing = image(102L, listing, "same-sha", "0000000000000000");
        ListingImage duplicate = image(103L, related, "same-sha", "ffffffffffffffff");
        when(imageRepository.findById(101L)).thenReturn(Optional.of(image));
        when(imageRepository.findAnalyzedImagesBySellerExcluding(
                        eq(55L), eq(101L), any(Pageable.class)))
                .thenReturn(List.of(sameListing, duplicate));
        when(riskRepository.findByFingerprint("SIMILAR_IMAGE:55:900:1001"))
                .thenReturn(Optional.empty());

        service.analyzeImage(101L);

        ArgumentCaptor<ModerationRiskSignal> signalCaptor =
                ArgumentCaptor.forClass(ModerationRiskSignal.class);
        verify(riskRepository).save(signalCaptor.capture());
        assertThat(signalCaptor.getValue().getType()).isEqualTo(ModerationRiskType.SIMILAR_IMAGE);
        assertThat(signalCaptor.getValue().getScore()).isEqualTo(100);
    }

    @Test
    void perceptuallySimilarImageRefreshesExistingSignalAndIgnoresWeakCandidates() {
        Listing listing = listing(1001L, "Current");
        Listing related = listing(1002L, "Related");
        ListingImage image = image(101L, listing, "sha-a", "0000000000000000");
        ListingImage similar = image(102L, related, "sha-b", "0000000000000003");
        ListingImage missingHash = image(103L, related, "sha-c", null);
        ListingImage different = image(104L, related, "sha-d", "ffffffffffffffff");
        ModerationRiskSignal existing = signal(
                701L, 55L, 1001L, 1002L, ModerationRiskType.SIMILAR_IMAGE, 80);
        when(imageRepository.findById(101L)).thenReturn(Optional.of(image));
        when(imageRepository.findAnalyzedImagesBySellerExcluding(
                        eq(55L), eq(101L), any(Pageable.class)))
                .thenReturn(List.of(similar, missingHash, different));
        when(riskRepository.findByFingerprint("SIMILAR_IMAGE:55:1001:1002"))
                .thenReturn(Optional.of(existing));

        service.analyzeImage(101L);

        assertThat(existing.getScore()).isEqualTo(97);
        verify(riskRepository, never()).save(any());
    }

    @Test
    void imageAnalysisRejectsUnknownImage() {
        when(imageRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyzeImage(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_IMAGE_NOT_FOUND));
    }

    @Test
    void dashboardAggregatesMetricsAndRanksSuspiciousSellers() {
        List<ModerationRiskSignal> signals = List.of(
                signal(701L, 1L, 101L, null, ModerationRiskType.PUBLISHING_VELOCITY, 100),
                signal(702L, 1L, 102L, null, ModerationRiskType.SIMILAR_TITLE, 90),
                signal(703L, 1L, 103L, null, ModerationRiskType.SIMILAR_IMAGE, 95),
                signal(704L, 1L, 104L, null, ModerationRiskType.SIMILAR_TITLE, 91),
                signal(705L, 1L, 105L, null, ModerationRiskType.SIMILAR_IMAGE, 92),
                signal(706L, 2L, 201L, null, ModerationRiskType.PUBLISHING_VELOCITY, 100),
                signal(707L, 2L, 202L, null, ModerationRiskType.SIMILAR_TITLE, 90),
                signal(708L, 3L, 301L, null, ModerationRiskType.SIMILAR_IMAGE, 95));
        when(riskRepository.findByStatusOrderByCreatedAtDesc(
                        eq(ModerationRiskStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(signals));
        when(historyRepository.countFirstPublicationsBySellerSince(any()))
                .thenReturn(List.of(projection(1L, 60L), projection(2L, 30L), projection(3L, 1L)));
        Member member = Member.createLocal("seller2@example.com", "encoded", "seller-two", null);
        ReflectionTestUtils.setField(member, "id", 2L);
        when(memberRepository.findAllById(any())).thenReturn(List.of(member));
        when(reportRepository.countByStatus(ListingReportStatus.PENDING)).thenReturn(4L);
        when(listingRepository.countByModerationStatusAndDeletedAtIsNull(
                        ListingModerationStatus.WARNING_ACK_REQUIRED))
                .thenReturn(3L);
        when(listingRepository.countByModerationStatusAndDeletedAtIsNull(
                        ListingModerationStatus.SUSPENDED))
                .thenReturn(2L);
        when(restorationRepository.countByStatus(RestorationRequestStatus.PENDING)).thenReturn(1L);
        when(riskRepository.countByStatus(ModerationRiskStatus.OPEN)).thenReturn(8L);

        var response = service.dashboard();

        assertThat(response.pendingReportCount()).isEqualTo(4L);
        assertThat(response.warningRequiredProductCount()).isEqualTo(3L);
        assertThat(response.suspendedProductCount()).isEqualTo(2L);
        assertThat(response.pendingRestorationCount()).isEqualTo(1L);
        assertThat(response.openRiskSignalCount()).isEqualTo(8L);
        assertThat(response.suspiciousSellers())
                .extracting("sellerId", "riskLevel")
                .containsExactly(tuple(1L, "HIGH"), tuple(2L, "MEDIUM"), tuple(3L, "LOW"));
        assertThat(response.suspiciousSellers().get(1).nickname()).isEqualTo("seller-two");
        assertThat(response.recentRiskSignals()).hasSize(8);
    }

    @Test
    void filteredRiskSignalsReturnPageMetadata() {
        ModerationRiskSignal signal =
                signal(701L, 55L, 1001L, 1002L, ModerationRiskType.SIMILAR_TITLE, 90);
        PageRequest pageable = PageRequest.of(1, 2);
        when(riskRepository.findAll(
                        org.mockito.ArgumentMatchers.<Specification<ModerationRiskSignal>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(signal), pageable, 3));

        var response = service.signals(
                ModerationRiskStatus.OPEN, ModerationRiskType.SIMILAR_TITLE, 1, 2);

        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.riskSignalId()).isEqualTo(701L);
            assertThat(item.signalType()).isEqualTo("SIMILAR_TITLE");
        });
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(3);
    }

    @Test
    void moderatedProductsApplyEveryFilterAndIncludeSellerMetrics() {
        Listing listing = listing(1001L, "LG Gram");
        Member seller = Member.createLocal("seller@example.com", "encoded", "seller", null);
        ReflectionTestUtils.setField(seller, "id", 55L);
        when(listingRepository.findAll(
                        org.mockito.ArgumentMatchers.<Specification<Listing>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));
        when(memberRepository.findAllById(any())).thenReturn(List.of(seller));
        when(reportRepository.countByListingIdAndStatus(1001L, ListingReportStatus.PENDING))
                .thenReturn(2L);
        when(riskRepository.countByListingIdAndStatus(1001L, ModerationRiskStatus.OPEN))
                .thenReturn(3L);

        var response = service.products(
                " gram ", 55L, ListingStatus.ON_SALE, ListingModerationStatus.NORMAL, 0, 20);

        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(1001L);
            assertThat(item.sellerNickname()).isEqualTo("seller");
            assertThat(item.pendingReportCount()).isEqualTo(2L);
            assertThat(item.openRiskSignalCount()).isEqualTo(3L);
        });
    }

    @Test
    void moderatedProductsAllowEmptyFiltersAndMissingMember() {
        Listing listing = listing(1001L, "LG Gram");
        when(listingRepository.findAll(
                        org.mockito.ArgumentMatchers.<Specification<Listing>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));
        when(memberRepository.findAllById(any())).thenReturn(List.of());

        var response = service.products(" ", null, null, null, 0, 20);

        assertThat(response.getContent().get(0).sellerNickname()).isNull();
    }

    @Test
    void titleSimilarityIgnoresSpacingAndPunctuation() {
        assertThat(ModerationRiskService.titleSimilarity(
                        "LG 그램-16인치 (2025)", "LG그램 16인치 2025"))
                .isEqualTo(100);
        assertThat(ModerationRiskService.titleSimilarity(
                        "LG 그램 노트북", "아이폰 프로 판매"))
                .isLessThan(50);
        assertThat(ModerationRiskService.titleSimilarity(null, "")).isEqualTo(100);
        assertThat(ModerationRiskService.titleSimilarity("a", "a")).isEqualTo(100);
        assertThat(ModerationRiskService.titleSimilarity("a", "b")).isZero();
    }

    @Test
    void resolvingRiskOnlyRecordsHumanReview() {
        ModerationRiskSignal signal = ModerationRiskSignal.create(
                55L,
                1001L,
                null,
                ModerationRiskType.PUBLISHING_VELOCITY,
                100,
                "7일 동안 30개 상품 판매 시작",
                "PUBLISHING_VELOCITY:55:1001");
        ReflectionTestUtils.setField(signal, "id", 701L);
        when(riskRepository.findById(701L)).thenReturn(Optional.of(signal));

        var response = service.resolve(9L, 701L, "회원 활동과 상품을 직접 확인함");

        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(signal.getResolvedByAdminId()).isEqualTo(9L);
        verify(actionLogRepository).save(any());
    }

    @Test
    void resolvingUnknownRiskIsRejected() {
        when(riskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(9L, 404L, "checked"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RISK_SIGNAL_NOT_FOUND));
    }

    @Test
    void invalidPagingIsRejected() {
        assertThatThrownBy(() -> service.signals(null, null, -1, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.products(null, null, null, null, 0, 101))
                .isInstanceOf(BusinessException.class);
    }

    private Listing listing(Long id, String title) {
        Category category = Category.createTopLevel("노트북", DeviceType.LAPTOP, 0);
        Listing listing = Listing.createDraft(55L, category, title, "상태 양호", 500_000, 10L);
        ReflectionTestUtils.setField(listing, "id", id);
        ReflectionTestUtils.setField(listing, "deviceModelId", 101L);
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ON_SALE);
        return listing;
    }

    private ListingImage image(
            Long id, Listing listing, String contentSha256, String perceptualHash) {
        ListingImage image = ListingImage.create(
                listing, ListingImageType.DETAIL, "products/" + id, null, "image/png");
        ReflectionTestUtils.setField(image, "id", id);
        image.recordHashes(contentSha256, perceptualHash, LocalDateTime.now());
        return image;
    }

    private ModerationRiskSignal signal(
            Long id,
            Long sellerId,
            Long listingId,
            Long relatedListingId,
            ModerationRiskType type,
            int score) {
        ModerationRiskSignal signal = ModerationRiskSignal.create(
                sellerId,
                listingId,
                relatedListingId,
                type,
                score,
                "risk detail",
                type + ":" + sellerId + ":" + id);
        ReflectionTestUtils.setField(signal, "id", id);
        return signal;
    }

    private SellerPublishingCountProjection projection(Long sellerId, long count) {
        return new SellerPublishingCountProjection() {
            @Override
            public Long getSellerId() {
                return sellerId;
            }

            @Override
            public long getPublishingCount() {
                return count;
            }
        };
    }
}
