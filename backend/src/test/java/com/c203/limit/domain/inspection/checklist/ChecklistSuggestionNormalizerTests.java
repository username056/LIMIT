package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChecklistSuggestionNormalizerTests {

    @Test
    void removesDifferentFeaturesThatResolveToTheSameChecklistItem() {
        LaptopChecklistPolicy laptopPolicy = mock(LaptopChecklistPolicy.class);
        DeviceChecklistFeatureCatalog featureCatalog = mock(DeviceChecklistFeatureCatalog.class);
        DeviceChecklistFeatureCatalog.FeatureDefinition first = definition("FIRST");
        DeviceChecklistFeatureCatalog.FeatureDefinition second = definition("SECOND");
        when(featureCatalog.supports(DeviceType.SMARTPHONE, "FIRST")).thenReturn(true);
        when(featureCatalog.supports(DeviceType.SMARTPHONE, "SECOND")).thenReturn(true);
        when(featureCatalog.find(DeviceType.SMARTPHONE, "FIRST"))
                .thenReturn(Optional.of(first));
        when(featureCatalog.find(DeviceType.SMARTPHONE, "SECOND"))
                .thenReturn(Optional.of(second));
        ChecklistSuggestionNormalizer normalizer =
                new ChecklistSuggestionNormalizer(laptopPolicy, featureCatalog);

        List<ChecklistSuggestion> normalized = normalizer.normalize(
                DeviceType.SMARTPHONE,
                List.of(
                        suggestion("FIRST", "https://www.example.com/first"),
                        suggestion("SECOND", "https://www.example.com/second")));

        assertThat(normalized)
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.featureCode()).isEqualTo("FIRST");
                    assertThat(suggestion.itemCode()).isEqualTo("SHARED-ITEM");
                    assertThat(suggestion.evidenceType()).isEqualTo(EvidenceType.VIDEO);
                });
    }

    private DeviceChecklistFeatureCatalog.FeatureDefinition definition(String code) {
        return new DeviceChecklistFeatureCatalog.FeatureDefinition(
                code, "기능", "목적", "확인 방법", "SHARED-ITEM", EvidenceType.VIDEO);
    }

    private ChecklistSuggestion suggestion(String featureCode, String sourceUrl) {
        return new ChecklistSuggestion(
                featureCode,
                "기능",
                ChecklistEvidenceStatus.VERIFIED,
                "공식 자료에서 확인했습니다.",
                "실제 동작을 확인하세요.",
                sourceUrl,
                "공식 지원 페이지");
    }
}
