package com.c203.limit.domain.inspection.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceInfoCompletionServiceTests {

    private static final Long ITEM_ID = 55L;

    @Mock ListingChecklistItemRepository listingChecklistItemRepository;
    @Mock DiagnosisAggregationService diagnosisAggregationService;
    @Mock ListingChecklistItem item;

    private DeviceInfoCompletionService service() {
        return new DeviceInfoCompletionService(listingChecklistItemRepository, diagnosisAggregationService);
    }

    private void stubValue(DiagnosisFieldName fieldName, String value) {
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, fieldName))
                .thenReturn(new DiagnosisAggregationService.DiagnosisFieldValue(null, value, 1L, null));
    }

    // 진단 프로그램이 올린 DxDiag에서 값이 채워지면 사진 없이도 확인된 것으로 본다.
    @Test
    void completesWhenEveryFieldIsFilled() {
        when(item.getItemCode()).thenReturn("LAP-SCR-013");
        when(item.getId()).thenReturn(ITEM_ID);
        when(item.getCompletionStatus()).thenReturn(ChecklistItemCompletionStatus.PENDING);
        stubValue(DiagnosisFieldName.MODEL_NAME, "960XGL");
        stubValue(DiagnosisFieldName.STORAGE_CAPACITY, "975.7 GB");
        stubValue(DiagnosisFieldName.OS_VERSION, "Windows 11 Enterprise 64-bit");
        stubValue(DiagnosisFieldName.CPU, "Intel(R) Core(TM) Ultra 7 155H");

        service().markIfFilled(item);

        verify(item).markCompleted();
        verify(listingChecklistItemRepository).save(item);
    }

    @Test
    void leavesItemPendingWhileAnyFieldIsEmpty() {
        when(item.getItemCode()).thenReturn("SYS-003");
        when(item.getId()).thenReturn(ITEM_ID);
        when(item.getCompletionStatus()).thenReturn(ChecklistItemCompletionStatus.PENDING);
        stubValue(DiagnosisFieldName.MODEL_NAME, "960XGL");
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.STORAGE_CAPACITY))
                .thenReturn(new DiagnosisAggregationService.DiagnosisFieldValue(null, "  ", null, null));

        service().markIfFilled(item);

        verify(item, never()).markCompleted();
    }

    // 사진·영상으로 확인하는 항목은 진단값으로 대신할 수 없다.
    @Test
    void ignoresItemsThatAreNotDeviceInfo() {
        when(item.getItemCode()).thenReturn("LAP-KBD-005");

        service().markIfFilled(item);

        verify(item, never()).markCompleted();
        verify(diagnosisAggregationService, never())
                .getFieldValue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // 이미 완료된 항목은 다시 쓰지 않는다. 필요 없는 UPDATE는 같은 행을 두고 다투게 만든다.
    @Test
    void doesNotWriteWhenAlreadyCompleted() {
        when(item.getItemCode()).thenReturn("SYS-003");
        when(item.getCompletionStatus()).thenReturn(ChecklistItemCompletionStatus.COMPLETED);

        service().markIfFilled(item);

        verify(item, never()).markCompleted();
        verify(listingChecklistItemRepository, never()).save(item);
    }
}
