package com.c203.limit.domain.product.moderation.service;

import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingImage;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.moderation.dto.response.*;
import com.c203.limit.domain.product.moderation.entity.*;
import com.c203.limit.domain.product.moderation.repository.ListingReportRepository;
import com.c203.limit.domain.product.moderation.repository.ListingRestorationRequestRepository;
import com.c203.limit.domain.product.moderation.repository.ModerationRiskSignalRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.SellerPublishingCountProjection;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationRiskService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TITLE_CANDIDATES = 300;
    private static final int MAX_IMAGE_CANDIDATES = 500;

    private final ListingRepository listingRepository;
    private final ListingStatusHistoryRepository historyRepository;
    private final ListingImageRepository imageRepository;
    private final ListingReportRepository reportRepository;
    private final ListingRestorationRequestRepository restorationRepository;
    private final ModerationRiskSignalRepository riskRepository;
    private final MemberRepository memberRepository;
    private final AdminActionLogRepository actionLogRepository;
    private final Clock clock;
    private final int publishingThreshold;
    private final int publishingWindowDays;
    private final int titleSimilarityThreshold;
    private final int imageHammingThreshold;

    public ModerationRiskService(
            ListingRepository listingRepository,
            ListingStatusHistoryRepository historyRepository,
            ListingImageRepository imageRepository,
            ListingReportRepository reportRepository,
            ListingRestorationRequestRepository restorationRepository,
            ModerationRiskSignalRepository riskRepository,
            MemberRepository memberRepository,
            AdminActionLogRepository actionLogRepository,
            Clock clock,
            @Value("${limit.moderation.publishing-threshold:30}") int publishingThreshold,
            @Value("${limit.moderation.publishing-window-days:7}") int publishingWindowDays,
            @Value("${limit.moderation.title-similarity-threshold:85}") int titleSimilarityThreshold,
            @Value("${limit.moderation.image-hamming-threshold:8}") int imageHammingThreshold) {
        this.listingRepository = listingRepository;
        this.historyRepository = historyRepository;
        this.imageRepository = imageRepository;
        this.reportRepository = reportRepository;
        this.restorationRepository = restorationRepository;
        this.riskRepository = riskRepository;
        this.memberRepository = memberRepository;
        this.actionLogRepository = actionLogRepository;
        this.clock = clock;
        this.publishingThreshold = publishingThreshold;
        this.publishingWindowDays = publishingWindowDays;
        this.titleSimilarityThreshold = titleSimilarityThreshold;
        this.imageHammingThreshold = imageHammingThreshold;
    }

    @Transactional
    public void analyzePublication(Listing listing) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(publishingWindowDays);
        long count = historyRepository.countFirstPublicationsBySellerSince(listing.getSellerId(), since);
        if (count >= publishingThreshold) {
            riskRepository
                    .findFirstBySellerIdAndTypeAndStatus(
                            listing.getSellerId(),
                            ModerationRiskType.PUBLISHING_VELOCITY,
                            ModerationRiskStatus.OPEN)
                    .ifPresentOrElse(
                            signal -> signal.refresh(
                                    velocityScore(count),
                                    publishingWindowDays + "일 동안 " + count + "개 상품 판매 시작"),
                            () -> riskRepository.save(ModerationRiskSignal.create(
                                    listing.getSellerId(),
                                    listing.getId(),
                                    null,
                                    ModerationRiskType.PUBLISHING_VELOCITY,
                                    velocityScore(count),
                                    publishingWindowDays + "일 동안 " + count + "개 상품 판매 시작",
                                    "PUBLISHING_VELOCITY:" + listing.getSellerId() + ":" + listing.getId())));
        }
        analyzeTitle(listing);
    }

    @Transactional
    public void analyzeImage(Long imageId) {
        ListingImage image = imageRepository
                .findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_IMAGE_NOT_FOUND));
        Listing listing = image.getListing();
        List<ListingImage> candidates = imageRepository.findAnalyzedImagesBySellerExcluding(
                listing.getSellerId(), imageId, PageRequest.of(0, MAX_IMAGE_CANDIDATES));
        for (ListingImage candidate : candidates) {
            if (candidate.getListing().getId().equals(listing.getId())) continue;
            int score = imageSimilarity(image, candidate);
            if (score < imageSimilarityThreshold()) continue;
            long first = Math.min(listing.getId(), candidate.getListing().getId());
            long second = Math.max(listing.getId(), candidate.getListing().getId());
            saveOrRefresh(ModerationRiskSignal.create(
                    listing.getSellerId(),
                    first,
                    second,
                    ModerationRiskType.SIMILAR_IMAGE,
                    score,
                    "같은 판매자의 유사 상품 이미지 감지 (유사도 " + score + "%)",
                    "SIMILAR_IMAGE:" + listing.getSellerId() + ":" + first + ":" + second));
        }
    }

    @Transactional(readOnly = true)
    public ModerationDashboardResponse dashboard() {
        Page<ModerationRiskSignal> openPage = riskRepository.findByStatusOrderByCreatedAtDesc(
                ModerationRiskStatus.OPEN, PageRequest.of(0, 500));
        List<ModerationRiskSignal> openSignals = openPage.getContent();
        LocalDateTime since = LocalDateTime.now(clock).minusDays(publishingWindowDays);
        Map<Long, Long> publishingCounts = historyRepository
                .countFirstPublicationsBySellerSince(since)
                .stream()
                .collect(Collectors.toMap(
                        SellerPublishingCountProjection::getSellerId,
                        SellerPublishingCountProjection::getPublishingCount));
        Map<Long, Long> riskCounts = openSignals.stream()
                .collect(Collectors.groupingBy(ModerationRiskSignal::getSellerId, Collectors.counting()));
        Set<Long> sellerIds = new HashSet<>(riskCounts.keySet());
        publishingCounts.forEach((sellerId, count) -> {
            if (count >= publishingThreshold) sellerIds.add(sellerId);
        });
        Map<Long, Member> members = memberRepository.findAllById(sellerIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        List<SuspiciousSellerResponse> suspiciousSellers = sellerIds.stream()
                .map(sellerId -> suspiciousSeller(
                        sellerId,
                        members.get(sellerId),
                        publishingCounts.getOrDefault(sellerId, 0L),
                        riskCounts.getOrDefault(sellerId, 0L)))
                .sorted(Comparator.comparingInt(
                                (SuspiciousSellerResponse response) -> riskRank(response.riskLevel()))
                        .thenComparingLong(SuspiciousSellerResponse::openRiskSignalCount)
                        .reversed())
                .limit(20)
                .toList();
        return new ModerationDashboardResponse(
                reportRepository.countByStatus(ListingReportStatus.PENDING),
                listingRepository.countByModerationStatusAndDeletedAtIsNull(
                        ListingModerationStatus.WARNING_ACK_REQUIRED),
                listingRepository.countByModerationStatusAndDeletedAtIsNull(
                        ListingModerationStatus.SUSPENDED),
                restorationRepository.countByStatus(RestorationRequestStatus.PENDING),
                riskRepository.countByStatus(ModerationRiskStatus.OPEN),
                suspiciousSellers,
                openSignals.stream().limit(20).map(this::riskResponse).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ModerationRiskSignalResponse> signals(
            ModerationRiskStatus status, ModerationRiskType type, int page, int size) {
        validatePage(page, size);
        Specification<ModerationRiskSignal> spec =
                (root, query, builder) -> builder.conjunction();
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("type"), type));
        }
        Page<ModerationRiskSignal> result = riskRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return page(result, result.stream().map(this::riskResponse).toList());
    }

    @Transactional
    public ModerationRiskSignalResponse resolve(Long adminId, Long signalId, String note) {
        ModerationRiskSignal signal = riskRepository
                .findById(signalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RISK_SIGNAL_NOT_FOUND));
        signal.resolve(adminId, note, LocalDateTime.now(clock));
        actionLogRepository.save(AdminActionLog.of(
                adminId, "MODERATION_RISK_RESOLVE", "MODERATION_RISK_SIGNAL", signalId, note));
        return riskResponse(signal);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminModeratedProductResponse> products(
            String keyword,
            Long sellerId,
            ListingStatus lifecycleStatus,
            ListingModerationStatus moderationStatus,
            int page,
            int size) {
        validatePage(page, size);
        Specification<Listing> spec = (root, query, builder) -> builder.isNull(root.get("deletedAt"));
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, builder) -> builder.like(builder.lower(root.get("title")), pattern));
        }
        if (sellerId != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("sellerId"), sellerId));
        }
        if (lifecycleStatus != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), lifecycleStatus));
        }
        if (moderationStatus != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("moderationStatus"), moderationStatus));
        }
        Page<Listing> result = listingRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        Map<Long, Member> members = memberRepository
                .findAllById(result.getContent().stream().map(Listing::getSellerId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        List<AdminModeratedProductResponse> content = result.stream()
                .map(listing -> {
                    Member seller = members.get(listing.getSellerId());
                    return new AdminModeratedProductResponse(
                            listing.getId(),
                            listing.getTitle(),
                            listing.getSellerId(),
                            seller == null ? null : seller.getNickname(),
                            listing.getStatus().name(),
                            listing.getModerationStatus().name(),
                            reportRepository.countByListingIdAndStatus(
                                    listing.getId(), ListingReportStatus.PENDING),
                            riskRepository.countByListingIdAndStatus(
                                    listing.getId(), ModerationRiskStatus.OPEN),
                            listing.getCreatedAt(),
                            listing.getUpdatedAt());
                })
                .toList();
        return page(result, content);
    }

    private void analyzeTitle(Listing listing) {
        Page<Listing> candidates = listingRepository.findBySellerIdAndIdNotAndDeletedAtIsNull(
                listing.getSellerId(), listing.getId(), PageRequest.of(0, MAX_TITLE_CANDIDATES));
        for (Listing candidate : candidates) {
            if (!Objects.equals(listing.getDeviceModelId(), candidate.getDeviceModelId())) continue;
            int score = titleSimilarity(listing.getTitle(), candidate.getTitle());
            if (score < titleSimilarityThreshold) continue;
            long first = Math.min(listing.getId(), candidate.getId());
            long second = Math.max(listing.getId(), candidate.getId());
            saveOrRefresh(ModerationRiskSignal.create(
                    listing.getSellerId(),
                    first,
                    second,
                    ModerationRiskType.SIMILAR_TITLE,
                    score,
                    "같은 판매자의 유사 상품명 감지 (유사도 " + score + "%)",
                    "SIMILAR_TITLE:" + listing.getSellerId() + ":" + first + ":" + second));
        }
    }

    private void saveOrRefresh(ModerationRiskSignal candidate) {
        riskRepository.findByFingerprint(candidate.getFingerprint()).ifPresentOrElse(
                existing -> existing.refresh(candidate.getScore(), candidate.getDetail()),
                () -> riskRepository.save(candidate));
    }

    static int titleSimilarity(String first, String second) {
        Set<String> firstBigrams = bigrams(normalizeTitle(first));
        Set<String> secondBigrams = bigrams(normalizeTitle(second));
        if (firstBigrams.isEmpty() || secondBigrams.isEmpty()) {
            return normalizeTitle(first).equals(normalizeTitle(second)) ? 100 : 0;
        }
        Set<String> intersection = new HashSet<>(firstBigrams);
        intersection.retainAll(secondBigrams);
        Set<String> union = new HashSet<>(firstBigrams);
        union.addAll(secondBigrams);
        return (int) Math.round(intersection.size() * 100.0 / union.size());
    }

    private static String normalizeTitle(String title) {
        return title == null
                ? ""
                : title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Set<String> bigrams(String value) {
        if (value.length() < 2) return value.isEmpty() ? Set.of() : Set.of(value);
        Set<String> result = new HashSet<>();
        for (int index = 0; index < value.length() - 1; index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }

    private int imageSimilarity(ListingImage first, ListingImage second) {
        if (first.getContentSha256() != null
                && first.getContentSha256().equals(second.getContentSha256())) return 100;
        if (first.getPerceptualHash() == null || second.getPerceptualHash() == null) return 0;
        long firstHash = Long.parseUnsignedLong(first.getPerceptualHash(), 16);
        long secondHash = Long.parseUnsignedLong(second.getPerceptualHash(), 16);
        int distance = Long.bitCount(firstHash ^ secondHash);
        return distance <= imageHammingThreshold
                ? (int) Math.round((64 - distance) * 100.0 / 64)
                : 0;
    }

    private int imageSimilarityThreshold() {
        return (int) Math.round((64 - imageHammingThreshold) * 100.0 / 64);
    }

    private int velocityScore(long count) {
        return Math.min(100, (int) Math.round(count * 100.0 / publishingThreshold));
    }

    private SuspiciousSellerResponse suspiciousSeller(
            Long sellerId, Member member, long publishedCount, long riskCount) {
        String level = publishedCount >= publishingThreshold * 2L || riskCount >= 5
                ? "HIGH"
                : publishedCount >= publishingThreshold || riskCount >= 2 ? "MEDIUM" : "LOW";
        return new SuspiciousSellerResponse(
                sellerId,
                member == null ? null : member.getNickname(),
                publishedCount,
                riskCount,
                level);
    }

    private int riskRank(String level) {
        return switch (level) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private ModerationRiskSignalResponse riskResponse(ModerationRiskSignal signal) {
        return new ModerationRiskSignalResponse(
                signal.getId(),
                signal.getSellerId(),
                signal.getListingId(),
                signal.getRelatedListingId(),
                signal.getType().name(),
                signal.getScore(),
                signal.getDetail(),
                signal.getStatus().name(),
                signal.getResolutionNote(),
                signal.getResolvedAt(),
                signal.getCreatedAt());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private <T, R> PageResponse<R> page(Page<T> source, List<R> content) {
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext());
    }
}
