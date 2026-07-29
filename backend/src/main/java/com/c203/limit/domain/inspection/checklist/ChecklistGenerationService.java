package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ChecklistGenerationService {
    private final CategoryRepository categoryRepository;
    private final LaptopChecklistPolicy policy;
    private final ChecklistSupplementClient supplementClient;

    public ChecklistGenerationService(
            CategoryRepository categoryRepository,
            LaptopChecklistPolicy policy,
            ChecklistSupplementClient supplementClient) {
        this.categoryRepository = categoryRepository;
        this.policy = policy;
        this.supplementClient = supplementClient;
    }

    public GeneratedChecklist generateForModel(
            Long deviceModelId, Set<LaptopFeatureCode> confirmedFeatures) {
        Category model = activeModel(deviceModelId);
        requireLaptop(model);
        return generate(context(
                model.getId(),
                model.getManufacturer(),
                model.getName(),
                model.getModelCode(),
                model.getOsFamily(),
                confirmedFeatures));
    }

    public Optional<GeneratedChecklist> generateSnapshotIfLaptop(
            Long deviceModelId, Set<LaptopFeatureCode> confirmedFeatures) {
        Category model = activeModel(deviceModelId);
        if (model.getDeviceType() != DeviceType.LAPTOP) {
            return Optional.empty();
        }
        return Optional.of(generatePolicyOnly(context(
                model.getId(),
                model.getManufacturer(),
                model.getName(),
                model.getModelCode(),
                model.getOsFamily(),
                confirmedFeatures)));
    }

    public GeneratedChecklist generateCustom(
            String manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            Set<LaptopFeatureCode> confirmedFeatures) {
        if (isBlank(manufacturer) || isBlank(modelName) || osFamily == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return generate(context(
                null,
                manufacturer.trim(),
                modelName.trim(),
                trimToNull(modelCode),
                osFamily,
                confirmedFeatures));
    }

    private GeneratedChecklist generate(ChecklistGenerationContext context) {
        ChecklistSupplementResult supplement;
        try {
            supplement = supplementClient.suggest(context);
        } catch (RuntimeException exception) {
            supplement = ChecklistSupplementResult.unavailable();
        }

        List<ChecklistSuggestion> suggestions = validSuggestions(supplement.suggestions());
        Map<LaptopFeatureCode, ChecklistSuggestion> suggestionByFeature = new LinkedHashMap<>();
        suggestions.forEach(suggestion ->
                suggestionByFeature.putIfAbsent(suggestion.featureCode(), suggestion));

        List<GeneratedChecklistItem> items =
                new ArrayList<>(policy.generate(context.osFamily(), context.confirmedFeatures()));
        for (int index = 0; index < items.size(); index++) {
            GeneratedChecklistItem item = items.get(index);
            ChecklistSuggestion suggestion = suggestionByFeature.get(item.featureCode());
            if (suggestion != null) {
                items.set(
                        index,
                        new GeneratedChecklistItem(
                                item.itemCode(),
                                item.name(),
                                item.purpose(),
                                item.guide(),
                                item.evidenceType(),
                                item.automationType(),
                                item.parserType(),
                                item.required(),
                                item.displayOrder(),
                                item.featureCode(),
                                suggestion.evidenceStatus(),
                                suggestion.sourceUrl(),
                                suggestion.sourceTitle()));
            }
        }

        return new GeneratedChecklist(
                context.deviceModelId(),
                context.manufacturer(),
                context.modelName(),
                context.osFamily(),
                1,
                supplement.available(),
                items,
                suggestions,
                supplement.reviewCandidates().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .filter(value -> !isSupportedFeatureCode(value))
                        .distinct()
                        .limit(LaptopChecklistPolicy.MAX_ADDITIONAL_ITEMS)
                        .toList());
    }

    private GeneratedChecklist generatePolicyOnly(ChecklistGenerationContext context) {
        return new GeneratedChecklist(
                context.deviceModelId(),
                context.manufacturer(),
                context.modelName(),
                context.osFamily(),
                1,
                false,
                policy.generate(context.osFamily(), context.confirmedFeatures()),
                List.of(),
                List.of());
    }

    private Category activeModel(Long deviceModelId) {
        return categoryRepository
                .findById(deviceModelId)
                .filter(Category::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    private List<ChecklistSuggestion> validSuggestions(List<ChecklistSuggestion> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        Map<LaptopFeatureCode, ChecklistSuggestion> unique = new LinkedHashMap<>();
        suggestions.stream()
                .filter(suggestion -> suggestion != null && suggestion.featureCode() != null)
                .filter(suggestion -> policy.supports(suggestion.featureCode()))
                .filter(suggestion -> isSafeSource(suggestion.sourceUrl()))
                .limit(LaptopChecklistPolicy.MAX_ADDITIONAL_ITEMS)
                .forEach(suggestion -> unique.putIfAbsent(suggestion.featureCode(), suggestion));
        return List.copyOf(unique.values());
    }

    private ChecklistGenerationContext context(
            Long deviceModelId,
            String manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            Set<LaptopFeatureCode> confirmedFeatures) {
        requireSupportedOs(osFamily);
        Set<LaptopFeatureCode> normalized;
        if (confirmedFeatures == null || confirmedFeatures.isEmpty()) {
            normalized = Set.of();
        } else {
            normalized = EnumSet.copyOf(confirmedFeatures);
        }
        if (normalized.size() > LaptopChecklistPolicy.MAX_ADDITIONAL_ITEMS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new ChecklistGenerationContext(
                deviceModelId,
                manufacturer,
                modelName,
                modelCode,
                osFamily,
                normalized);
    }

    private void requireLaptop(Category model) {
        if (model.getDeviceType() != DeviceType.LAPTOP) {
            throw new BusinessException(ErrorCode.CHECKLIST_DEVICE_TYPE_NOT_SUPPORTED);
        }
    }

    private void requireSupportedOs(OsFamily osFamily) {
        if (osFamily != OsFamily.WINDOWS && osFamily != OsFamily.LINUX) {
            throw new BusinessException(ErrorCode.CHECKLIST_OS_NOT_SUPPORTED);
        }
    }

    private boolean isSafeSource(String sourceUrl) {
        if (isBlank(sourceUrl)) {
            return false;
        }
        try {
            URI uri = URI.create(sourceUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && !isBlank(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isSupportedFeatureCode(String value) {
        try {
            LaptopFeatureCode featureCode =
                    LaptopFeatureCode.valueOf(value.toUpperCase(Locale.ROOT));
            return policy.supports(featureCode);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
