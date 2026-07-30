package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.enums.DeviceType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeviceChecklistFeatureCatalogTests {
    private final DeviceChecklistFeatureCatalog catalog =
            new DeviceChecklistFeatureCatalog();

    @Test
    void providesAiResearchCandidatesForEverySupportedDeviceType() {
        assertThat(catalog.definitionsFor(DeviceType.SMARTPHONE)).isNotEmpty();
        assertThat(catalog.definitionsFor(DeviceType.FOLDABLE)).isNotEmpty();
        assertThat(catalog.definitionsFor(DeviceType.TABLET)).isNotEmpty();
        assertThat(catalog.definitionsFor(DeviceType.LAPTOP)).isNotEmpty();
    }

    @Test
    void createsDeviceSpecificChecklistItemsFromConfirmedFeatures() {
        assertThat(catalog.additionalItems(
                        DeviceType.FOLDABLE, Set.of("COVER_DISPLAY"), 5))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.itemCode()).isEqualTo("FLD-FTR-COVER");
                    assertThat(item.displayOrder()).isEqualTo(5);
                });
        assertThat(catalog.additionalItems(
                        DeviceType.TABLET, Set.of("KEYBOARD_CONNECTOR"), 5))
                .singleElement()
                .satisfies(item ->
                        assertThat(item.itemCode()).isEqualTo("TAB-FTR-KBD"));
    }
}
