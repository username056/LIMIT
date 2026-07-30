package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    void createsLinuxFallbackEvidenceWithoutWindowsParsers() {
        var items = policy.generate(OsFamily.LINUX, Set.of());

        assertThat(items).hasSize(12);
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
}
