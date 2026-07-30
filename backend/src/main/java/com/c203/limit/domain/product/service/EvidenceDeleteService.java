package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자가 잘못 올린 증빙을 지우고, 재업로드가 막히지 않도록 관련 상태를 정리하는 유스케이스. */
@Service
public class EvidenceDeleteService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceDeleteService.class);

    private final ListingRepository listingRepository;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final EvidenceRepository evidenceRepository;
    private final BatteryReportResultRepository batteryReportResultRepository;
    private final DxdiagResultRepository dxdiagResultRepository;
    private final OcrResultRepository ocrResultRepository;
    private final S3MediaProperties properties;
    private final ApplicationEventPublisher events;

    public EvidenceDeleteService(
            ListingRepository listingRepository,
            ListingChecklistItemRepository checklistItemRepository,
            EvidenceRepository evidenceRepository,
            BatteryReportResultRepository batteryReportResultRepository,
            DxdiagResultRepository dxdiagResultRepository,
            OcrResultRepository ocrResultRepository,
            S3MediaProperties properties,
            ApplicationEventPublisher events) {
        this.listingRepository = listingRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.evidenceRepository = evidenceRepository;
        this.batteryReportResultRepository = batteryReportResultRepository;
        this.dxdiagResultRepository = dxdiagResultRepository;
        this.ocrResultRepository = ocrResultRepository;
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public void delete(Long sellerId, Long productId, Long checklistItemId, Long evidenceId) {
        Listing listing = ownedListing(sellerId, productId);
        ListingChecklistItem item = checklistItemRepository
                .findByIdAndListingId(checklistItemId, listing.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        Evidence evidence = evidenceRepository
                .findByIdAndListingId(evidenceId, listing.getId())
                .filter(candidate ->
                        candidate.getListingChecklistItem().getId().equals(checklistItemId))
                .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));

        batteryReportResultRepository.deleteByEvidenceId(evidenceId);
        dxdiagResultRepository.deleteByEvidenceId(evidenceId);
        ocrResultRepository.deleteByEvidenceId(evidenceId);
        evidenceRepository.delete(evidence);
        evidenceRepository.flush();

        long remaining = evidenceRepository.countByListingChecklistItem_Id(checklistItemId);
        int requiredCount = item.getMinCount() == null ? 1 : item.getMinCount();
        boolean revertedToPending = remaining < requiredCount;
        if (revertedToPending) {
            item.markPending();
        }

        events.publishEvent(new EvidenceDeletedEvent(properties.bucket(), evidence.getS3Key()));
        log.info(
                "evidence deleted: evidenceId={}, checklistItemId={}, remaining={}, revertedToPending={}",
                evidenceId,
                checklistItemId,
                remaining,
                revertedToPending);
    }

    private Listing ownedListing(Long sellerId, Long productId) {
        Listing listing = listingRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        return listing;
    }
}
