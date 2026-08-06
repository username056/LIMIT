package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.dto.response.ProductChecklistItemResponse;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductChecklistService {

    private static final Logger log = LoggerFactory.getLogger(ProductChecklistService.class);

    private final ListingRepository listingRepository;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final EvidenceRepository evidenceRepository;

    public ProductChecklistService(
            ListingRepository listingRepository,
            ListingChecklistItemRepository checklistItemRepository,
            EvidenceRepository evidenceRepository) {
        this.listingRepository = listingRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductChecklistItemResponse> findAll(
            Long productId, String status, boolean requiredOnly) {
        return findAll(productId, status, requiredOnly, null);
    }

    @Transactional(readOnly = true)
    public List<ProductChecklistItemResponse> findAll(
            Long productId, String status, boolean requiredOnly, Long viewerMemberId) {
        var listing = listingRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> {
                    log.warn("product checklist lookup failed: productId={}", productId);
                    return new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                });
        boolean isOwner = viewerMemberId != null && viewerMemberId.equals(listing.getSellerId());
        if (!isOwner && !listing.isPubliclyVisible()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ChecklistItemCompletionStatus completionStatus = completionStatus(status);
        List<ListingChecklistItem> items = requiredOnly
                ? checklistItemRepository
                        .findByListingIdAndIsRequiredTrueOrderByDisplayOrderAsc(productId)
                : checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(productId);

        List<ListingChecklistItem> filteredItems = items.stream()
                .filter(item ->
                        completionStatus == null || item.getCompletionStatus() == completionStatus)
                .toList();
        if (filteredItems.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Evidence>> evidenceByItemId = evidenceRepository
                .findAllByListingId(productId)
                .stream()
                .collect(Collectors.groupingBy(
                        evidence -> evidence.getListingChecklistItem().getId()));

        return filteredItems.stream()
                .map(item -> response(
                        item, evidenceByItemId.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    private ProductChecklistItemResponse response(
            ListingChecklistItem item, List<Evidence> evidenceHistory) {
        Long latestEvidenceId = evidenceHistory.stream()
                .max(Comparator.comparing(
                                Evidence::getUploadedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Evidence::getId))
                .map(Evidence::getId)
                .orElse(null);

        return new ProductChecklistItemResponse(
                item.getId(),
                item.getItemCode(),
                item.getName(),
                item.getCaptureGuide(),
                item.getEvidenceType().name(),
                item.getAutomationType().name(),
                item.getParserType(),
                item.isRequired(),
                item.getCompletionStatus().name(),
                latestEvidenceId,
                evidenceHistory.size(),
                item.isVisibleToBuyer(),
                item.getMinCount(),
                item.getMaxCount(),
                item.getMaxFileSizeMb(),
                item.getMinDurationSec(),
                item.getMaxDurationSec());
    }

    private ChecklistItemCompletionStatus completionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ChecklistItemCompletionStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
