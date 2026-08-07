package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ChecklistSuggestionNormalizer {
    private final LaptopChecklistPolicy laptopPolicy;
    private final DeviceChecklistFeatureCatalog featureCatalog;

    public ChecklistSuggestionNormalizer(
            LaptopChecklistPolicy laptopPolicy,
            DeviceChecklistFeatureCatalog featureCatalog) {
        this.laptopPolicy = laptopPolicy;
        this.featureCatalog = featureCatalog;
    }

    public List<ChecklistSuggestion> normalize(
            DeviceType deviceType, List<ChecklistSuggestion> suggestions) {
        if (deviceType == null || suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }

        Map<String, ChecklistSuggestion> uniqueByFeatureCode = new LinkedHashMap<>();
        Set<String> uniqueItemCodes = new LinkedHashSet<>();
        for (ChecklistSuggestion suggestion : suggestions) {
            if (!isUsable(suggestion)) {
                continue;
            }

            String featureCode = normalizeFeatureCode(suggestion.featureCode());
            if (uniqueByFeatureCode.containsKey(featureCode)
                    || !featureCatalog.supports(deviceType, featureCode)
                    || !isSafeSource(suggestion.sourceUrl())) {
                continue;
            }

            String itemCode = itemCode(deviceType, featureCode);
            if (!uniqueItemCodes.add(itemCode)) {
                continue;
            }

            uniqueByFeatureCode.put(
                    featureCode,
                    new ChecklistSuggestion(
                            featureCode,
                            suggestion.featureName(),
                            suggestion.evidenceStatus(),
                            suggestion.reason(),
                            suggestion.checkGuide(),
                            suggestion.sourceUrl(),
                            suggestion.sourceTitle(),
                            evidenceType(deviceType, featureCode),
                            itemCode));
            if (uniqueByFeatureCode.size()
                    == DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS) {
                break;
            }
        }
        return List.copyOf(uniqueByFeatureCode.values());
    }

    private boolean isUsable(ChecklistSuggestion suggestion) {
        return suggestion != null
                && suggestion.featureCode() != null
                && !suggestion.featureCode().isBlank()
                && suggestion.evidenceStatus() != null;
    }

    private EvidenceType evidenceType(DeviceType deviceType, String featureCode) {
        if (deviceType == DeviceType.LAPTOP) {
            return laptopPolicy.evidenceType(LaptopFeatureCode.valueOf(featureCode));
        }
        return featureCatalog
                .find(deviceType, featureCode)
                .map(DeviceChecklistFeatureCatalog.FeatureDefinition::evidenceType)
                .orElseThrow();
    }

    private String itemCode(DeviceType deviceType, String featureCode) {
        if (deviceType == DeviceType.LAPTOP) {
            return laptopPolicy.itemCode(LaptopFeatureCode.valueOf(featureCode));
        }
        return featureCatalog
                .find(deviceType, featureCode)
                .map(DeviceChecklistFeatureCatalog.FeatureDefinition::itemCode)
                .orElseThrow();
    }

    private boolean isSafeSource(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(sourceUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String normalizeFeatureCode(String featureCode) {
        return featureCode.trim().toUpperCase(Locale.ROOT);
    }
}
