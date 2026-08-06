package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.dto.response.DiagnosisSummaryItem;
import com.c203.limit.domain.inspection.dto.response.ProductDiagnosisSummaryResponse;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.DiagnosisSummaryStatus;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자가 상품 상세에서 보는 검수 진단 최종 요약. 인증이 필요 없는 공개 조회 유스케이스로, 매물의 체크리스트
 * 항목마다 필드별 현재 값(ocr_result/dxdiag_result/battery_report_result을 그대로 반영, 판매자가 고쳤다면
 * 고친 값)을 보여준다.
 */
@Service
public class ProductDiagnosisSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ProductDiagnosisSummaryService.class);
    private static final String DISCLAIMER = "자동 추출값은 참고 정보이며 상품의 정상 여부를 보증하지 않습니다.";

    private final ListingOwnerReader listingOwnerReader;
    private final ListingChecklistItemRepository listingChecklistItemRepository;
    private final DiagnosisAggregationService diagnosisAggregationService;
    private final EvidenceRepository evidenceRepository;

    public ProductDiagnosisSummaryService(
            ListingOwnerReader listingOwnerReader,
            ListingChecklistItemRepository listingChecklistItemRepository,
            DiagnosisAggregationService diagnosisAggregationService,
            EvidenceRepository evidenceRepository) {
        this.listingOwnerReader = listingOwnerReader;
        this.listingChecklistItemRepository = listingChecklistItemRepository;
        this.diagnosisAggregationService = diagnosisAggregationService;
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional(readOnly = true)
    public ProductDiagnosisSummaryResponse getSummary(Long productId) {
        listingOwnerReader.findById(productId).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return buildSummary(productId);
    }

    @Transactional(readOnly = true)
    public ProductDiagnosisSummaryResponse getSummary(Long productId, Long viewerMemberId) {
        var visibility = listingOwnerReader
                .findVisibilityById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        boolean isOwner = viewerMemberId != null && viewerMemberId.equals(visibility.sellerId());
        if (!isOwner && !visibility.publiclyVisible()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return buildSummary(productId);
    }

    private ProductDiagnosisSummaryResponse buildSummary(Long productId) {

        List<ListingChecklistItem> checklistItems =
                listingChecklistItemRepository.findAllByListingIdOrderByDisplayOrderAsc(productId);

        // 같은 필드(예: CPU)를 여러 체크리스트 항목이 각자 취합할 수 있어(OCR 화면 항목과 DXDIAG 항목처럼),
        // 필드마다 항목별로 다 나열하지 않고 하나만 골라 돌려준다 — 값이 있는 쪽을 우선한다.
        List<DiagnosisSummaryItem> items =
                checklistItems.isEmpty()
                        ? List.of()
                        : Arrays.stream(DiagnosisFieldName.values())
                                .map(fieldName -> bestSummaryItem(checklistItems, fieldName))
                                .toList();

        boolean allExtractionsFailed =
                !checklistItems.isEmpty() && items.stream().noneMatch(item -> item.status() == DiagnosisSummaryStatus.AVAILABLE);
        if (allExtractionsFailed) {
            log.warn("product diagnosis summary has no available fields: productId={}", productId);
        }

        return new ProductDiagnosisSummaryResponse(productId, items, DISCLAIMER);
    }

    private DiagnosisSummaryItem bestSummaryItem(
            List<ListingChecklistItem> checklistItems, DiagnosisFieldName fieldName) {
        DiagnosisSummaryItem fallback = null;
        for (ListingChecklistItem item : checklistItems) {
            DiagnosisSummaryItem candidate = toSummaryItem(item.getId(), fieldName);
            if (candidate.status() == DiagnosisSummaryStatus.AVAILABLE) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    private DiagnosisSummaryItem toSummaryItem(Long itemId, DiagnosisFieldName fieldName) {
        DiagnosisAggregationService.DiagnosisFieldValue fieldValue =
                diagnosisAggregationService.getFieldValue(itemId, fieldName);
        String value = fieldValue.fileParseValue() != null ? fieldValue.fileParseValue() : fieldValue.ocrValue();

        DiagnosisSummaryStatus status =
                value != null ? DiagnosisSummaryStatus.AVAILABLE : DiagnosisSummaryStatus.EXTRACTION_FAILED;
        String originalFileUrl = resolveOriginalFileUrl(fieldValue.sourceEvidenceId());

        return new DiagnosisSummaryItem(fieldName.name(), originalFileUrl, value, status);
    }

    private String resolveOriginalFileUrl(Long sourceEvidenceId) {
        if (sourceEvidenceId == null) {
            return null;
        }
        return evidenceRepository.findById(sourceEvidenceId).map(Evidence::getCdnUrl).orElse(null);
    }
}
