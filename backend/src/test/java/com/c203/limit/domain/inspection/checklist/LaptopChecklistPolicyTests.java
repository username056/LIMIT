package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.product.entity.OsFamily;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LaptopChecklistPolicyTests {
    private final LaptopChecklistPolicy policy = new LaptopChecklistPolicy();

    @Test
    void createsWindowsDiagnosticsAsRequiredFiles() {
        var items = policy.generate(OsFamily.WINDOWS, Set.of());

        assertThat(items).hasSize(12);
        assertThat(items)
                .noneMatch(item -> item.itemCode().equals("LAP-CHG-007"));
        assertThat(items)
                .filteredOn(item -> item.itemCode().equals("LAP-HNG-012"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.name()).isEqualTo("힌지 상태");
                    assertThat(item.evidenceType()).isEqualTo(EvidenceType.VIDEO);
                    assertThat(item.required()).isTrue();
                });
        assertThat(items)
                .filteredOn(item -> item.itemCode().equals("LAP-BAT-010"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.evidenceType()).isEqualTo(EvidenceType.DIAGNOSTIC_FILE);
                    assertThat(item.parserType()).isEqualTo("BATTERY_REPORT");
                    assertThat(item.required()).isTrue();
                });
        assertThat(items)
                .filteredOn(item -> item.itemCode().equals("LAP-SYS-011"))
                .singleElement()
                .extracting(GeneratedChecklistItem::parserType)
                .isEqualTo("DXDIAG");
        assertThat(items)
                .filteredOn(item -> item.itemCode().equals("LAP-SCR-013"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.evidenceType()).isEqualTo(EvidenceType.PHOTO);
                    assertThat(item.automationType()).isEqualTo(AutomationType.OCR);
                    assertThat(item.parserType()).isNull();
                    assertThat(item.required()).isTrue();
                });
    }

    @Test
    void createsLinuxFallbackEvidenceWithoutWindowsParsers() {
        var items = policy.generate(OsFamily.LINUX, Set.of());

        assertThat(items).hasSize(11);
        assertThat(items)
                .filteredOn(item -> item.itemCode().equals("LAP-BAT-010")
                        || item.itemCode().equals("LAP-SYS-011"))
                .allSatisfy(item -> {
                    assertThat(item.evidenceType()).isEqualTo(EvidenceType.PHOTO);
                    assertThat(item.parserType()).isNull();
                });
    }

    @Test
    void addsAtMostFiveConfirmedFeaturesWithoutDuplicates() {
        var items = policy.generate(
                OsFamily.WINDOWS,
                Set.of(
                        LaptopFeatureCode.CAMERA,
                        LaptopFeatureCode.WIFI,
                        LaptopFeatureCode.BLUETOOTH,
                        LaptopFeatureCode.OLED,
                        LaptopFeatureCode.NUMPAD));

        assertThat(items).hasSize(17);
        assertThat(items.stream()
                        .filter(item -> item.featureCode() != null)
                        .map(GeneratedChecklistItem::featureCode))
                .containsExactlyInAnyOrder(
                        "CAMERA",
                        "WIFI",
                        "BLUETOOTH",
                        "OLED",
                        "NUMPAD");
    }

    @Test
    void addsRj45AndMicroSdAsSeparateConfirmedFeatures() {
        var items = policy.generate(
                OsFamily.WINDOWS,
                Set.of(LaptopFeatureCode.RJ45_PORT, LaptopFeatureCode.MICROSD_SLOT));

        assertThat(items).hasSize(14);
        assertThat(items.stream()
                        .filter(item -> item.featureCode() != null)
                        .map(GeneratedChecklistItem::featureCode))
                .containsExactlyInAnyOrder(
                        "RJ45_PORT",
                        "MICROSD_SLOT");
        assertThat(items)
                .filteredOn(item -> "RJ45_PORT".equals(item.featureCode()))
                .singleElement()
                .extracting(GeneratedChecklistItem::name)
                .isEqualTo("유선 LAN(RJ45) 포트");
        assertThat(items)
                .filteredOn(item -> "MICROSD_SLOT".equals(item.featureCode()))
                .singleElement()
                .extracting(GeneratedChecklistItem::name)
                .isEqualTo("microSD 카드 슬롯");
    }

    @Test
    void marksBaseDeviceActionCheckItemsAsSellerConfirmation() {
        var items = policy.generate(OsFamily.WINDOWS, Set.of());

        assertThat(items)
                .filteredOn(item -> item.itemCode().equals("LAP-KBD-005")
                        || item.itemCode().equals("LAP-PAD-006"))
                .hasSize(2)
                .allSatisfy(item -> assertThat(item.evidenceType()).isEqualTo(EvidenceType.SELLER_CONFIRMATION));
    }

    @Test
    void createsOnlySelectedAdditionalItemsAfterPublishedTemplateOrder() {
        var items = policy.additionalItems(
                Set.of(LaptopFeatureCode.PORTS, LaptopFeatureCode.SPEAKERS), 7);

        assertThat(items)
                .extracting(GeneratedChecklistItem::itemCode)
                .containsExactly("LAP-FTR-PORT", "LAP-FTR-SPK");
        assertThat(items)
                .extracting(GeneratedChecklistItem::displayOrder)
                .containsExactly(7, 8);
        assertThat(items)
                .extracting(GeneratedChecklistItem::evidenceType)
                .containsExactly(EvidenceType.VIDEO, EvidenceType.SELLER_CONFIRMATION);
    }

    @Test
    void marksFeatureDeviceActionCheckItemsAsSellerConfirmation() {
        var items = policy.generate(
                OsFamily.WINDOWS,
                Set.of(
                        LaptopFeatureCode.CAMERA,
                        LaptopFeatureCode.MICROPHONE,
                        LaptopFeatureCode.SPEAKERS,
                        LaptopFeatureCode.TOUCHSCREEN,
                        LaptopFeatureCode.STYLUS));

        assertThat(items)
                .filteredOn(item -> Set.of("LAP-FTR-CAM", "LAP-FTR-MIC", "LAP-FTR-SPK", "LAP-FTR-TOUCH", "LAP-FTR-PEN")
                        .contains(item.itemCode()))
                .hasSize(5)
                .allSatisfy(item -> assertThat(item.evidenceType()).isEqualTo(EvidenceType.SELLER_CONFIRMATION));

        var numpadItems = policy.generate(OsFamily.WINDOWS, Set.of(LaptopFeatureCode.NUMPAD));
        assertThat(numpadItems)
                .filteredOn(item -> item.itemCode().equals("LAP-FTR-NUM"))
                .singleElement()
                .extracting(GeneratedChecklistItem::evidenceType)
                .isEqualTo(EvidenceType.SELLER_CONFIRMATION);
    }

    @Test
    void marksCameraMicrophoneSpeakerTouchscreenStylusAsSpecOnlyNotRequired() {
        // MAX_ADDITIONAL_ITEMS(5)를 넘기지 않도록 스펙 전용 대상 5개만 확인한다.
        var items = policy.generate(
                OsFamily.WINDOWS,
                Set.of(
                        LaptopFeatureCode.CAMERA,
                        LaptopFeatureCode.MICROPHONE,
                        LaptopFeatureCode.SPEAKERS,
                        LaptopFeatureCode.TOUCHSCREEN,
                        LaptopFeatureCode.STYLUS));

        assertThat(items)
                .filteredOn(item -> Set.of(
                                "LAP-FTR-CAM", "LAP-FTR-MIC", "LAP-FTR-SPK", "LAP-FTR-TOUCH", "LAP-FTR-PEN")
                        .contains(item.itemCode()))
                .hasSize(5)
                .allSatisfy(item -> {
                    assertThat(item.required()).isFalse();
                    // 검증한 적 없는 항목이 구매자 체크리스트에 미완료로 비치지 않도록 buyer 화면에서도 숨긴다.
                    assertThat(item.visibleToBuyer()).isFalse();
                });

        var portsItems = policy.generate(OsFamily.WINDOWS, Set.of(LaptopFeatureCode.PORTS));
        assertThat(portsItems)
                .filteredOn(item -> item.itemCode().equals("LAP-FTR-PORT"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.required()).isTrue();
                    assertThat(item.visibleToBuyer()).isTrue();
                });
    }

    // 숫자 키패드는 셋과 달리 웹 점검(useKeyboardCheck includeNumpad)으로 실제 완료가
    // 가능해서 필수 항목으로 남긴다.
    @Test
    void keepsNumpadAsRequiredBecauseWebCheckCanCompleteIt() {
        var items = policy.generate(OsFamily.WINDOWS, Set.of(LaptopFeatureCode.NUMPAD));

        assertThat(items)
                .filteredOn(item -> item.itemCode().equals("LAP-FTR-NUM"))
                .singleElement()
                .satisfies(item -> assertThat(item.required()).isTrue());
    }
}
