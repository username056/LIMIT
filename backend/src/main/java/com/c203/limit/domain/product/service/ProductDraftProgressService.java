package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest;
import com.c203.limit.domain.product.dto.response.ProductDraftProgressResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDraftProgressService {
    private static final Logger log = LoggerFactory.getLogger(ProductDraftProgressService.class);

    private final ListingRepository listings;
    private final ListingChecklistItemRepository checklistItems;

    public ProductDraftProgressService(
            ListingRepository listings,
            ListingChecklistItemRepository checklistItems) {
        this.listings = listings;
        this.checklistItems = checklistItems;
    }

    @Transactional(readOnly = true)
    public ProductDraftProgressResponse find(Long sellerId, Long productId) {
        Listing listing = owned(sellerId, productId);
        return response(listing, checklistItems.findByListingIdOrderByDisplayOrderAsc(productId));
    }

    @Transactional
    public ProductDraftProgressResponse update(
            Long sellerId, Long productId, UpdateProductDraftProgressRequest request) {
        Listing listing = owned(sellerId, productId);
        List<ListingChecklistItem> items =
                checklistItems.findByListingIdOrderByDisplayOrderAsc(productId);

        List<UpdateProductDraftProgressRequest.ChecklistItemResult> results = request.results();
        long distinctItemCount = results.stream()
                .map(UpdateProductDraftProgressRequest.ChecklistItemResult::checklistItemId)
                .distinct()
                .count();
        if (distinctItemCount != results.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Map<Long, DeviceCheckResult> requested = results.stream()
                .collect(Collectors.toMap(
                        UpdateProductDraftProgressRequest.ChecklistItemResult::checklistItemId,
                        UpdateProductDraftProgressRequest.ChecklistItemResult::result));

        Set<Long> confirmationIds = new HashSet<>();
        for (ListingChecklistItem item : items) {
            if (item.getEvidenceType() != EvidenceType.SELLER_CONFIRMATION) continue;
            confirmationIds.add(item.getId());
            DeviceCheckResult result = requested.get(item.getId());
            if (result != null) item.applyDeviceCheckResult(result);
            else item.markPending();
        }
        if (!confirmationIds.containsAll(requested.keySet())) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        listing.updateDraftStep(request.step());
        log.info(
                "Product draft progress updated: productId={}, step={}, checklistResultCount={}",
                productId,
                request.step(),
                requested.size());
        return response(listing, items);
    }

    private Listing owned(Long sellerId, Long productId) {
        return listings.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductDraftProgressResponse response(
            Listing listing, List<ListingChecklistItem> items) {
        Map<Long, DeviceCheckResult> results = items.stream()
                .filter(item -> item.getEvidenceType() == EvidenceType.SELLER_CONFIRMATION)
                .filter(item -> item.getDeviceCheckResult() != null)
                .collect(Collectors.toMap(
                        ListingChecklistItem::getId, ListingChecklistItem::getDeviceCheckResult));
        return new ProductDraftProgressResponse(listing.getDraftStep(), results);
    }
}
