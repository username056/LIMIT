package com.c203.limit.domain.product.moderation.service;

import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.moderation.dto.request.AdminModerationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.AdminRestorationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.CreateListingReportRequest;
import com.c203.limit.domain.product.moderation.dto.request.CreateRestorationRequest;
import com.c203.limit.domain.product.moderation.dto.response.*;
import com.c203.limit.domain.product.moderation.entity.*;
import com.c203.limit.domain.product.moderation.repository.ListingReportRepository;
import com.c203.limit.domain.product.moderation.repository.ListingRestorationRequestRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingModerationService {
    private static final Logger log = LoggerFactory.getLogger(ListingModerationService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final ListingRepository listingRepository;
    private final ListingReportRepository reportRepository;
    private final ListingRestorationRequestRepository restorationRepository;
    private final AdminActionLogRepository actionLogRepository;
    private final Clock clock;

    public ListingModerationService(
            ListingRepository listingRepository,
            ListingReportRepository reportRepository,
            ListingRestorationRequestRepository restorationRepository,
            AdminActionLogRepository actionLogRepository,
            Clock clock) {
        this.listingRepository = listingRepository;
        this.reportRepository = reportRepository;
        this.restorationRepository = restorationRepository;
        this.actionLogRepository = actionLogRepository;
        this.clock = clock;
    }

    @Transactional
    public ListingReportCreatedResponse report(
            Long reporterId, Long productId, CreateListingReportRequest request) {
        Listing listing = listingRepository
                .findByIdAndDeletedAtIsNull(productId)
                .filter(Listing::isPubliclyVisible)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.getSellerId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        if (reportRepository.existsByListingIdAndReporterId(productId, reporterId)) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_EXISTS);
        }
        ListingReport saved = reportRepository.saveAndFlush(ListingReport.create(
                productId, reporterId, request.category(), request.detail()));
        log.info("listing report created: reportId={}, productId={}", saved.getId(), productId);
        return new ListingReportCreatedResponse(
                saved.getId(), productId, saved.getCategory().name(), saved.getStatus().name(), saved.getCreatedAt());
    }

    @Transactional
    public ModerationActionResponse acknowledgeWarning(Long sellerId, Long productId) {
        Listing listing = owned(productId, sellerId);
        listing.acknowledgeModerationWarning();
        LocalDateTime now = LocalDateTime.now(clock);
        reportRepository
                .findByListingIdAndStatusOrderByCreatedAtAsc(
                        productId, ListingReportStatus.WARNING_ISSUED)
                .forEach(report -> report.acknowledge(now));
        log.info("listing moderation warning acknowledged: productId={}", productId);
        return new ModerationActionResponse(productId, listing.getModerationStatus().name());
    }

    @Transactional
    public RestorationRequestResponse requestRestoration(
            Long sellerId, Long productId, CreateRestorationRequest request) {
        Listing listing = owned(productId, sellerId);
        if (restorationRepository.existsByListingIdAndStatus(
                productId, RestorationRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.RESTORATION_PENDING_ALREADY_EXISTS);
        }
        listing.requestModerationRestoration();
        ListingRestorationRequest saved = restorationRepository.saveAndFlush(
                ListingRestorationRequest.create(productId, sellerId, request.requestNote()));
        log.info("listing restoration requested: requestId={}, productId={}", saved.getId(), productId);
        return restorationResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SellerModerationNoticeResponse> sellerNotices(Long productId) {
        return reportRepository
                .findByListingIdAndStatusInOrderByReviewedAtDesc(
                        productId,
                        List.of(ListingReportStatus.WARNING_ISSUED, ListingReportStatus.SUSPENDED))
                .stream()
                .map(report -> new SellerModerationNoticeResponse(
                        report.getId(),
                        report.getCategory().name(),
                        report.getDetail(),
                        report.getAdminNote(),
                        report.getReviewedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminListingReportResponse> reports(
            ListingReportStatus status, int page, int size) {
        validatePage(page, size);
        Specification<ListingReport> spec = status == null
                ? null
                : (root, query, builder) -> builder.equal(root.get("status"), status);
        Page<ListingReport> result = reportRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<Long, Listing> listings = listingRepository
                .findAllById(result.getContent().stream().map(ListingReport::getListingId).toList())
                .stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        return page(result, result.stream().map(report -> adminReport(report, listings.get(report.getListingId()))).toList());
    }

    @Transactional
    public AdminListingReportResponse decideReport(
            Long adminId, Long reportId, AdminModerationDecisionRequest request) {
        ListingReport report = reportRepository
                .findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        Listing listing = listingRepository
                .findByIdAndDeletedAtIsNull(report.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        switch (request.decision()) {
            case DISMISS -> { }
            case WARN -> listing.issueModerationWarning(request.note());
            case SUSPEND -> listing.suspendForModeration(request.note());
        }
        report.decide(request.decision(), adminId, request.note(), LocalDateTime.now(clock));
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "LISTING_REPORT_" + request.decision().name(),
                "LISTING_REPORT",
                reportId,
                request.note()));
        log.info(
                "listing report decided: reportId={}, productId={}, decision={}",
                reportId,
                listing.getId(),
                request.decision());
        return adminReport(report, listing);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRestorationRequestResponse> restorationRequests(
            RestorationRequestStatus status, int page, int size) {
        validatePage(page, size);
        Specification<ListingRestorationRequest> spec = status == null
                ? null
                : (root, query, builder) -> builder.equal(root.get("status"), status);
        Page<ListingRestorationRequest> result = restorationRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<Long, Listing> listings = listingRepository
                .findAllById(result.getContent().stream().map(ListingRestorationRequest::getListingId).toList())
                .stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        return page(result, result.stream()
                .map(request -> adminRestoration(request, listings.get(request.getListingId())))
                .toList());
    }

    @Transactional
    public AdminRestorationRequestResponse decideRestoration(
            Long adminId, Long requestId, AdminRestorationDecisionRequest request) {
        ListingRestorationRequest restoration = restorationRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTORATION_REQUEST_NOT_FOUND));
        Listing listing = listingRepository
                .findByIdAndDeletedAtIsNull(restoration.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (request.decision() == RestorationDecision.APPROVE) {
            listing.approveModerationRestoration();
        } else {
            listing.rejectModerationRestoration(request.note());
        }
        restoration.decide(request.decision(), adminId, request.note(), LocalDateTime.now(clock));
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "LISTING_RESTORATION_" + request.decision().name(),
                "LISTING_RESTORATION_REQUEST",
                requestId,
                request.note()));
        log.info(
                "listing restoration decided: requestId={}, productId={}, decision={}",
                requestId,
                listing.getId(),
                request.decision());
        return adminRestoration(restoration, listing);
    }

    private Listing owned(Long productId, Long sellerId) {
        return listingRepository
                .findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED));
    }

    private AdminListingReportResponse adminReport(ListingReport report, Listing listing) {
        return new AdminListingReportResponse(
                report.getId(),
                report.getListingId(),
                listing == null ? null : listing.getTitle(),
                listing == null ? null : listing.getSellerId(),
                report.getReporterId(),
                report.getCategory().name(),
                report.getDetail(),
                report.getStatus().name(),
                listing == null ? null : listing.getStatus().name(),
                listing == null ? null : listing.getModerationStatus().name(),
                report.getReviewerAdminId(),
                report.getAdminNote(),
                report.getReviewedAt(),
                report.getCreatedAt());
    }

    private AdminRestorationRequestResponse adminRestoration(
            ListingRestorationRequest request, Listing listing) {
        return new AdminRestorationRequestResponse(
                request.getId(),
                request.getListingId(),
                listing == null ? null : listing.getTitle(),
                request.getSellerId(),
                request.getRequestNote(),
                request.getStatus().name(),
                listing == null ? null : listing.getModerationStatus().name(),
                request.getReviewerAdminId(),
                request.getReviewNote(),
                request.getReviewedAt(),
                request.getCreatedAt(),
                listing == null ? null : listing.getUpdatedAt());
    }

    private RestorationRequestResponse restorationResponse(ListingRestorationRequest request) {
        return new RestorationRequestResponse(
                request.getId(),
                request.getListingId(),
                request.getSellerId(),
                request.getRequestNote(),
                request.getStatus().name(),
                request.getReviewerAdminId(),
                request.getReviewNote(),
                request.getReviewedAt(),
                request.getCreatedAt());
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
