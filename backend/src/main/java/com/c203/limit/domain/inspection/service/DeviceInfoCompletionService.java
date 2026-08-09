package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 정보 항목은 사진 없이 값만 채워질 수 있다. 판매자가 직접 입력하거나, 진단 프로그램이 올린
 * DxDiag 파일에서 읽어 오는 경우다. 그때는 그 항목에 증빙 row가 생기지 않아
 * {@code EvidenceUploadCompletionService}가 손댈 일이 없고 항목이 PENDING에 남아 있었다.
 *
 * <p>판매자는 다 채웠는데 상품 목록의 검증 개수는 오르지 않았다. 값은 상세 페이지의 '자동 인식된
 * 사양' 카드에 멀쩡히 떠 있는데도 그랬다.
 *
 * <p>값이 어디서 왔는지는 따지지 않는다. {@link DiagnosisAggregationService}가 DxDiag·OCR·직접
 * 입력을 이미 한 값으로 합쳐 주므로, 그 결과가 네 칸 모두 차 있으면 확인된 것으로 본다. 판매하기
 * 화면이 '자동 입력 완료'라고 부르는 기준과 같다.
 */
@Service
public class DeviceInfoCompletionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceInfoCompletionService.class);
    private static final Set<String> DEVICE_INFO_ITEM_CODES = Set.of("LAP-SCR-013", "SYS-003");
    private static final List<DiagnosisFieldName> DEVICE_INFO_FIELDS = List.of(
            DiagnosisFieldName.MODEL_NAME,
            DiagnosisFieldName.STORAGE_CAPACITY,
            DiagnosisFieldName.OS_VERSION,
            DiagnosisFieldName.CPU);

    private final ListingChecklistItemRepository listingChecklistItemRepository;
    private final DiagnosisAggregationService diagnosisAggregationService;

    public DeviceInfoCompletionService(
            ListingChecklistItemRepository listingChecklistItemRepository,
            DiagnosisAggregationService diagnosisAggregationService) {
        this.listingChecklistItemRepository = listingChecklistItemRepository;
        this.diagnosisAggregationService = diagnosisAggregationService;
    }

    /** 이 매물의 기기 정보 항목을 다시 보고, 네 값이 다 찼으면 완료로 둔다. */
    @Transactional
    public void refreshFor(Long listingId) {
        if (listingId == null) return;
        listingChecklistItemRepository.findByListingIdOrderByDisplayOrderAsc(listingId)
                .forEach(this::markIfFilled);
    }

    /**
     * 이미 들고 있는 항목 하나에 대해 같은 판단을 한다. 값을 방금 고친 직후에 부르는 쪽을 위한 것으로,
     * 같은 트랜잭션 안이라면 고친 값이 그대로 보인다.
     */
    @Transactional
    public void markIfFilled(ListingChecklistItem item) {
        if (item == null || item.getItemCode() == null) return;
        if (!DEVICE_INFO_ITEM_CODES.contains(item.getItemCode())) return;
        // 상태가 실제로 바뀔 때만 쓴다. 필요 없는 UPDATE는 같은 행을 두고 다투게 만든다.
        if (item.getCompletionStatus() == ChecklistItemCompletionStatus.COMPLETED) return;

        boolean allFilled = DEVICE_INFO_FIELDS.stream().allMatch(field -> {
            DiagnosisAggregationService.DiagnosisFieldValue value =
                    diagnosisAggregationService.getFieldValue(item.getId(), field);
            return hasText(value.fileParseValue()) || hasText(value.ocrValue());
        });
        if (!allFilled) return;

        item.markCompleted();
        listingChecklistItemRepository.save(item);
        log.info("device info item completed without evidence: itemId={}", item.getId());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
