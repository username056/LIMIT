package com.c203.limit.domain.product.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskSignal;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskType;
import com.c203.limit.domain.product.moderation.repository.ListingReportRepository;
import com.c203.limit.domain.product.moderation.repository.ListingRestorationRequestRepository;
import com.c203.limit.domain.product.moderation.repository.ModerationRiskSignalRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.data.domain.Pageable;
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
    void titleSimilarityIgnoresSpacingAndPunctuation() {
        assertThat(ModerationRiskService.titleSimilarity(
                        "LG 그램-16인치 (2025)", "LG그램 16인치 2025"))
                .isEqualTo(100);
        assertThat(ModerationRiskService.titleSimilarity(
                        "LG 그램 노트북", "아이폰 프로 판매"))
                .isLessThan(50);
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

    private Listing listing(Long id, String title) {
        Category category = Category.createTopLevel("노트북", DeviceType.LAPTOP, 0);
        Listing listing = Listing.createDraft(55L, category, title, "상태 양호", 500_000, 10L);
        ReflectionTestUtils.setField(listing, "id", id);
        ReflectionTestUtils.setField(listing, "deviceModelId", 101L);
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ON_SALE);
        return listing;
    }
}
