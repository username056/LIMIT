package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.agent.InspectionSessionTestResult;
import com.c203.limit.domain.inspection.agent.InspectionSessionTestResultRepository;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.InspectionUserResult;
import com.c203.limit.domain.inspection.enums.MeasurementStatus;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest;
import com.c203.limit.domain.product.dto.response.ProductDraftProgressResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.EnumMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDraftProgressService {
    private static final Logger log = LoggerFactory.getLogger(ProductDraftProgressService.class);
    // 스피커·디스플레이·충전·카메라·마이크는 실동작 점검에서 빠졌다. 특히 디스플레이·충전은
    // EvidenceType.VIDEO라 실제 영상 증빙 없이 이 목록에 있으면 device-check 결과만으로
    // COMPLETED 처리가 되어 영상 업로드를 우회할 수 있었다. 키보드·포인터만 남긴다.
    private static final Set<String> WEB_DEVICE_CHECK_ITEM_CODES = Set.of(
            "LAP-KBD-005",
            "LAP-PAD-006",
            "LAP-FTR-NUM");

    private final ListingRepository listings;
    private final ListingChecklistItemRepository checklistItems;
    private final InspectionSessionTestResultRepository automaticResults;

    public ProductDraftProgressService(
            ListingRepository listings,
            ListingChecklistItemRepository checklistItems,
            InspectionSessionTestResultRepository automaticResults) {
        this.listings = listings;
        this.checklistItems = checklistItems;
        this.automaticResults = automaticResults;
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

        Set<Long> checkableIds = new HashSet<>();
        for (ListingChecklistItem item : items) {
            if (!isWebDeviceCheckItem(item)) continue;
            checkableIds.add(item.getId());
            DeviceCheckResult result = requested.get(item.getId());
            if (result != null) item.applyDeviceCheckResult(result);
            else if (item.getEvidenceType() == EvidenceType.SELLER_CONFIRMATION) item.markPending();
        }
        if (!checkableIds.containsAll(requested.keySet())) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (request.deviceResults() != null) {
            List<UpdateProductDraftProgressRequest.WebDeviceResult> deviceResults =
                    request.deviceResults();
            long distinctTypeCount = deviceResults.stream()
                    .map(UpdateProductDraftProgressRequest.WebDeviceResult::testType)
                    .distinct()
                    .count();
            if (distinctTypeCount != deviceResults.size()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            Map<TestType, DeviceCheckResult> webResults = new EnumMap<>(TestType.class);
            deviceResults.forEach(result -> webResults.put(result.testType(), result.result()));
            listing.updateWebDeviceCheckResults(webResults);
        }
        /*
          단계 기록은 초안일 때만 남긴다.
          -----------------------------------------------------------------------
          updateDraftStep은 DRAFT가 아니면 PRODUCT_EDIT_NOT_ALLOWED(409)를 던진다. 그래서 이미
          판매 중인 상품을 고치면서 실동작 점검이나 개인정보 확인을 하면, 위에서 반영한 결과까지
          함께 롤백되어 "점검 결과를 저장하지 못했습니다"만 뜨고 아무것도 남지 않았다. 판매자
          입장에서는 분명히 점검했는데 계속 미점검으로 보였다.

          판매 중 상품 수정은 원래 허용하는 동작이다(PRODUCT_EDIT_NOT_ALLOWED 문구도 '초안·판매
          중·숨김 상태에서만 수정할 수 있다'고 말한다). 초안에서 어디까지 왔는지를 나타내는
          draftStep만 판매 중인 상품에 의미가 없을 뿐이라, 그 기록만 건너뛴다.
        */
        if (listing.getStatus() == ListingStatus.DRAFT) {
            listing.updateDraftStep(request.step());
        }
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
                .filter(this::isWebDeviceCheckItem)
                .filter(item -> item.getDeviceCheckResult() != null)
                .collect(Collectors.toMap(
                        ListingChecklistItem::getId, ListingChecklistItem::getDeviceCheckResult));
        Map<TestType, DeviceCheckResult> deviceResults = listing.getWebDeviceCheckResults() == null
                ? Map.of()
                : Map.copyOf(listing.getWebDeviceCheckResults());
        Map<TestType, DeviceCheckResult> automaticDeviceResults = new EnumMap<>(TestType.class);
        automaticResults.findAllByListingIdNewestFirst(listing.getId()).forEach(result ->
                automaticDeviceResults.putIfAbsent(result.getTestType(), automaticResult(result)));
        return new ProductDraftProgressResponse(
                listing.getDraftStep(), results, deviceResults, automaticDeviceResults);
    }

    private DeviceCheckResult automaticResult(InspectionSessionTestResult result) {
        if (result.getUserResult() == InspectionUserResult.SKIPPED) {
            return DeviceCheckResult.SKIPPED;
        }
        if (result.getUserResult() == InspectionUserResult.USER_REPORTED_ISSUE) {
            return DeviceCheckResult.FAILED;
        }
        if (result.getUserResult() == InspectionUserResult.USER_CONFIRMED) {
            return DeviceCheckResult.SUCCESS;
        }
        return result.getMeasurementStatus() == MeasurementStatus.DETECTED
                ? DeviceCheckResult.SUCCESS
                : DeviceCheckResult.FAILED;
    }

    private boolean isWebDeviceCheckItem(ListingChecklistItem item) {
        return WEB_DEVICE_CHECK_ITEM_CODES.contains(item.getItemCode());
    }
}
