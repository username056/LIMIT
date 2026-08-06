package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceHistoryService.class);

    private final ListingRepository listingRepository;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final EvidenceRepository evidenceRepository;
    private final MediaUrlResolver mediaUrlResolver;

    public EvidenceHistoryService(
            ListingRepository listingRepository,
            ListingChecklistItemRepository checklistItemRepository,
            EvidenceRepository evidenceRepository,
            MediaUrlResolver mediaUrlResolver) {
        this.listingRepository = listingRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.evidenceRepository = evidenceRepository;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> findAll(
            Long productId, Long checklistItemId, Long viewerMemberId) {
        Listing listing = listingRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        ListingChecklistItem item = checklistItemRepository
                .findByIdAndListingId(checklistItemId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        boolean isOwner = viewerMemberId != null && viewerMemberId.equals(listing.getSellerId());
        if (!isOwner && !listing.isPubliclyVisible()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!isOwner && !item.isVisibleToBuyer()) {
            log.info(
                    "Buyer-hidden evidence history access suppressed: productId={}, checklistItemId={}",
                    productId,
                    checklistItemId);
            return List.of();
        }

        List<Evidence> history =
                evidenceRepository.findAllByListingChecklistItem_IdOrderByUploadedAtAscIdAsc(
                        checklistItemId);
        Long latestId = history.isEmpty() ? null : history.get(history.size() - 1).getId();
        return java.util.stream.IntStream.range(0, history.size())
                .mapToObj(index -> response(history.get(index), index + 1, latestId))
                .toList();
    }

    private EvidenceResponse response(Evidence evidence, int attemptNo, Long latestId) {
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getListingChecklistItem().getId(),
                evidence.getEvidenceType().name(),
                attemptNo,
                evidence.getId().equals(latestId),
                mediaUrlResolver.resolve(evidence.getS3Key(), evidence.getCdnUrl()),
                evidence.getProcessingStatus().name(),
                "NONE",
                evidence.getCapturedAt() == null
                        ? null
                        : evidence.getCapturedAt().atOffset(ZoneOffset.UTC),
                evidence.getUploadedAt().atOffset(ZoneOffset.UTC));
    }
}
