package com.c203.limit.domain.inspection.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.ChecklistItemOrigin;
import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import org.junit.jupiter.api.Test;

class ListingChecklistItemTests {

    private static ChecklistTemplateItem templateItem() {
        return ChecklistTemplateItem.createGenerated(
                null,
                "SYSTEM_INFO",
                "설정 정보 화면",
                "기기 사양 확인",
                "설정 > 정보 화면을 촬영하세요",
                EvidenceType.PHOTO,
                AutomationType.OCR,
                "SYSTEM_INFO_OCR",
                true,
                3);
    }

    private static ListingChecklistItem baseItem() {
        return ListingChecklistItem.createFromTemplateItem(10L, templateItem());
    }

    @Test
    void createFromTemplateItemSnapshotsTemplateAndStartsPendingAsBaseItem() {
        ChecklistTemplateItem template = templateItem();

        ListingChecklistItem item = ListingChecklistItem.createFromTemplateItem(10L, template);

        assertThat(item.getListingId()).isEqualTo(10L);
        assertThat(item.getTemplateItem()).isSameAs(template);
        assertThat(item.getItemOrigin()).isEqualTo(ChecklistItemOrigin.BASE);
        assertThat(item.getFeatureCode()).isNull();
        assertThat(item.getItemCode()).isEqualTo("SYSTEM_INFO");
        assertThat(item.getName()).isEqualTo("설정 정보 화면");
        assertThat(item.getCaptureGuide()).isEqualTo(template.getCaptureGuide());
        assertThat(item.getEvidenceType()).isEqualTo(EvidenceType.PHOTO);
        assertThat(item.getAutomationType()).isEqualTo(AutomationType.OCR);
        assertThat(item.getParserType()).isEqualTo("SYSTEM_INFO_OCR");
        assertThat(item.isRequired()).isTrue();
        assertThat(item.getAllowedFormats()).isEqualTo(template.getAllowedFormats());
        assertThat(item.getMinCount()).isEqualTo(template.getMinCount());
        assertThat(item.getMaxCount()).isEqualTo(template.getMaxCount());
        assertThat(item.getMaxFileSizeMb()).isEqualTo(template.getMaxFileSizeMb());
        assertThat(item.getDisplayOrder()).isEqualTo(3);
        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.PENDING);
        assertThat(item.getDeviceCheckResult()).isNull();
    }

    @Test
    void createConfirmedFeatureItemRecordsSelectedFeatureCode() {
        ListingChecklistItem item =
                ListingChecklistItem.createConfirmedFeatureItem(10L, templateItem(), "S_PEN");

        assertThat(item.getItemOrigin()).isEqualTo(ChecklistItemOrigin.CONFIRMED_FEATURE);
        assertThat(item.getFeatureCode()).isEqualTo("S_PEN");
        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.PENDING);
    }

    @Test
    void markPendingResetsCompletionStatusAndDeviceCheckResult() {
        ListingChecklistItem item = baseItem();
        item.applyDeviceCheckResult(DeviceCheckResult.SUCCESS);

        item.markPending();

        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.PENDING);
        assertThat(item.getDeviceCheckResult()).isNull();
    }

    @Test
    void markSubmittedAndMarkCompletedMoveCompletionStatus() {
        ListingChecklistItem item = baseItem();

        item.markSubmitted();
        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.SUBMITTED);

        item.markCompleted();
        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.COMPLETED);
    }

    @Test
    void applyDeviceCheckResultCompletesOnlyOnSuccess() {
        ListingChecklistItem item = baseItem();

        item.applyDeviceCheckResult(DeviceCheckResult.SUCCESS);

        assertThat(item.getDeviceCheckResult()).isEqualTo(DeviceCheckResult.SUCCESS);
        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.COMPLETED);
    }

    @Test
    void applyDeviceCheckResultRollsBackCompletionWhenRecheckFails() {
        ListingChecklistItem item = baseItem();
        item.applyDeviceCheckResult(DeviceCheckResult.SUCCESS);

        item.applyDeviceCheckResult(DeviceCheckResult.FAILED);

        assertThat(item.getDeviceCheckResult()).isEqualTo(DeviceCheckResult.FAILED);
        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.SUBMITTED);
    }

    @Test
    void applyDeviceCheckResultKeepsSkippedItemSubmitted() {
        ListingChecklistItem item = baseItem();

        item.applyDeviceCheckResult(DeviceCheckResult.SKIPPED);

        assertThat(item.getDeviceCheckResult()).isEqualTo(DeviceCheckResult.SKIPPED);
        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.SUBMITTED);
    }

    @Test
    void correctManualDeviceInfoStoresValuePerSupportedField() {
        ListingChecklistItem item = baseItem();

        item.correctManualDeviceInfo(DiagnosisFieldName.MODEL_NAME, "SM-S921N");
        item.correctManualDeviceInfo(DiagnosisFieldName.STORAGE_CAPACITY, "256GB");
        item.correctManualDeviceInfo(DiagnosisFieldName.OS_VERSION, "Android 15");
        item.correctManualDeviceInfo(DiagnosisFieldName.CPU, "Snapdragon 8 Gen 3");

        assertThat(item.manualDiagnosisValue(DiagnosisFieldName.MODEL_NAME)).isEqualTo("SM-S921N");
        assertThat(item.manualDiagnosisValue(DiagnosisFieldName.STORAGE_CAPACITY))
                .isEqualTo("256GB");
        assertThat(item.manualDiagnosisValue(DiagnosisFieldName.OS_VERSION))
                .isEqualTo("Android 15");
        assertThat(item.manualDiagnosisValue(DiagnosisFieldName.CPU))
                .isEqualTo("Snapdragon 8 Gen 3");
    }

    @Test
    void manualDiagnosisValueReturnsNullForFieldsWithoutManualSlot() {
        ListingChecklistItem item = baseItem();

        assertThat(item.manualDiagnosisValue(DiagnosisFieldName.RAM)).isNull();
        assertThat(item.manualDiagnosisValue(DiagnosisFieldName.CYCLE_COUNT)).isNull();
        assertThat(item.manualDiagnosisValue(DiagnosisFieldName.MODEL_NAME)).isNull();
    }

    @Test
    void correctManualDeviceInfoRejectsFieldsWithoutManualSlot() {
        ListingChecklistItem item = baseItem();

        assertThatThrownBy(() -> item.correctManualDeviceInfo(DiagnosisFieldName.RAM, "16GB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RAM");
        assertThatThrownBy(
                        () -> item.correctManualDeviceInfo(
                                DiagnosisFieldName.DESIGN_CAPACITY, "5000"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
