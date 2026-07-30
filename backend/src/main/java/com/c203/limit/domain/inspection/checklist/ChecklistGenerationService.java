package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.ModelChecklistResearchRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ChecklistGenerationService {
    private static final int RESEARCH_VERSION = 1;

    private final CategoryRepository categoryRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final ModelChecklistResearchRepository researchRepository;
    private final LaptopChecklistPolicy laptopPolicy;
    private final DeviceChecklistFeatureCatalog featureCatalog;
    private final ChecklistSupplementClient supplementClient;
    private final ObjectMapper objectMapper;

    public ChecklistGenerationService(
            CategoryRepository categoryRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            ModelChecklistResearchRepository researchRepository,
            LaptopChecklistPolicy laptopPolicy,
            DeviceChecklistFeatureCatalog featureCatalog,
            ChecklistSupplementClient supplementClient,
            ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.researchRepository = researchRepository;
        this.laptopPolicy = laptopPolicy;
        this.featureCatalog = featureCatalog;
        this.supplementClient = supplementClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedChecklist generateForModel(
            Long deviceModelId, Set<String> confirmedFeatures) {
        Category model = activeModel(deviceModelId);
        ChecklistGenerationContext context = context(
                model.getId(),
                model.getDeviceType(),
                model.getManufacturer(),
                model.getName(),
                model.getModelCode(),
                model.getOsFamily(),
                confirmedFeatures);
        ModelChecklistResearch research = findOrResearch(model, context);
        return generatePolicyOnly(
                context, baseChecklist(model, context.confirmedFeatures()), research);
    }

    public Optional<GeneratedChecklist> generateSnapshotForModel(
            Long deviceModelId, Set<String> confirmedFeatures) {
        Category model = activeModel(deviceModelId);
        ChecklistGenerationContext context = context(
                model.getId(),
                model.getDeviceType(),
                model.getManufacturer(),
                model.getName(),
                model.getModelCode(),
                model.getOsFamily(),
                confirmedFeatures);
        if (!context.confirmedFeatures().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return Optional.empty();
    }

    public GeneratedChecklist generateCustom(
            String manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            Set<String> confirmedFeatures) {
        if (isBlank(manufacturer) || isBlank(modelName) || osFamily == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ChecklistGenerationContext context = context(
                null,
                DeviceType.LAPTOP,
                manufacturer.trim(),
                modelName.trim(),
                trimToNull(modelCode),
                osFamily,
                confirmedFeatures);
        return generateWithSupplement(
                context, laptopChecklist(osFamily, context.confirmedFeatures()));
    }

    private GeneratedChecklist generateWithSupplement(
            ChecklistGenerationContext context, BaseChecklist baseChecklist) {
        ChecklistSupplementResult supplement;
        try {
            supplement = supplementClient.suggest(context);
        } catch (RuntimeException exception) {
            supplement = ChecklistSupplementResult.unavailable();
        }

        List<ChecklistSuggestion> suggestions =
                validSuggestions(context.deviceType(), supplement.suggestions());
        Map<String, ChecklistSuggestion> suggestionByFeature = new LinkedHashMap<>();
        suggestions.forEach(suggestion ->
                suggestionByFeature.putIfAbsent(suggestion.featureCode(), suggestion));

        List<GeneratedChecklistItem> items = new ArrayList<>(baseChecklist.items());
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
                baseChecklist.templateVersion(),
                supplement.available(),
                items,
                suggestions,
                supplement.reviewCandidates().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .filter(value ->
                                !featureCatalog.supports(context.deviceType(), value))
                        .distinct()
                        .limit(DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS)
                        .toList(),
                null,
                null);
    }

    private GeneratedChecklist generatePolicyOnly(
            ChecklistGenerationContext context,
            BaseChecklist baseChecklist,
            ModelChecklistResearch research) {
        return new GeneratedChecklist(
                context.deviceModelId(),
                context.manufacturer(),
                context.modelName(),
                context.osFamily(),
                baseChecklist.templateVersion(),
                research != null
                        && research.getStatus() == ModelChecklistResearchStatus.APPROVED,
                baseChecklist.items(),
                List.of(),
                List.of(),
                research == null ? null : research.getId(),
                research == null ? null : research.getStatus().name());
    }

    private ModelChecklistResearch findOrResearch(
            Category model, ChecklistGenerationContext context) {
        Optional<ModelChecklistResearch> existing =
                researchRepository.findByCategoryIdAndResearchVersion(
                        model.getId(), RESEARCH_VERSION);
        if (existing.isPresent()) {
            return existing.get();
        }

        ModelChecklistResearch research;
        try {
            research = researchRepository.saveAndFlush(
                    ModelChecklistResearch.start(model.getId(), RESEARCH_VERSION));
        } catch (DataIntegrityViolationException exception) {
            return researchRepository
                    .findByCategoryIdAndResearchVersion(model.getId(), RESEARCH_VERSION)
                    .orElseThrow(() -> exception);
        }

        ChecklistSupplementResult supplement;
        try {
            supplement = supplementClient.suggest(context);
        } catch (RuntimeException exception) {
            research.fail();
            return researchRepository.saveAndFlush(research);
        }
        if (!supplement.available()) {
            research.fail();
            return researchRepository.saveAndFlush(research);
        }
        research.complete(writeSupplement(supplement));
        return researchRepository.saveAndFlush(research);
    }

    private String writeSupplement(ChecklistSupplementResult supplement) {
        try {
            return objectMapper.writeValueAsString(supplement);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize checklist research", exception);
        }
    }

    private BaseChecklist baseChecklist(Category model, Set<String> confirmedFeatures) {
        if (model.getDeviceType() == DeviceType.LAPTOP
                && !confirmedFeatures.isEmpty()) {
            return laptopChecklist(model.getOsFamily(), confirmedFeatures);
        }

        Optional<ChecklistTemplate> published = templateRepository
                .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        model.getId(), ChecklistTemplateStatus.PUBLISHED);
        if (published.isEmpty()) {
            if (model.getDeviceType() == DeviceType.LAPTOP) {
                return laptopChecklist(model.getOsFamily(), confirmedFeatures);
            }
            throw new BusinessException(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND);
        }
        ChecklistTemplate template = published.get();
        List<GeneratedChecklistItem> items = templateItemRepository
                .findByChecklistTemplateIdOrderByDisplayOrderAsc(template.getId())
                .stream()
                .map(this::templateItem)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        items.addAll(featureCatalog.additionalItems(
                model.getDeviceType(), confirmedFeatures, items.size() + 1));
        for (int index = 0; index < items.size(); index++) {
            items.set(index, items.get(index).withDisplayOrder(index + 1));
        }
        return new BaseChecklist(template.getVersion(), List.copyOf(items));
    }

    private BaseChecklist laptopChecklist(
            OsFamily osFamily, Set<String> confirmedFeatures) {
        Set<LaptopFeatureCode> laptopFeatures =
                confirmedFeatures.isEmpty()
                        ? Set.of()
                        : confirmedFeatures.stream()
                                .map(LaptopFeatureCode::valueOf)
                                .collect(java.util.stream.Collectors.toCollection(
                                        () -> EnumSet.noneOf(LaptopFeatureCode.class)));
        return new BaseChecklist(1, laptopPolicy.generate(osFamily, laptopFeatures));
    }

    private GeneratedChecklistItem templateItem(ChecklistTemplateItem item) {
        return new GeneratedChecklistItem(
                item.getItemCode(),
                item.getName(),
                item.getPurpose(),
                item.getCaptureGuide(),
                item.getEvidenceType(),
                item.getAutomationType(),
                item.getParserType(),
                item.isRequired(),
                item.getDisplayOrder(),
                null,
                null,
                null,
                null);
    }

    private Category activeModel(Long deviceModelId) {
        return categoryRepository
                .findById(deviceModelId)
                .filter(Category::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    private List<ChecklistSuggestion> validSuggestions(
            DeviceType deviceType, List<ChecklistSuggestion> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        Map<String, ChecklistSuggestion> unique = new LinkedHashMap<>();
        suggestions.stream()
                .filter(suggestion ->
                        suggestion != null && !isBlank(suggestion.featureCode()))
                .filter(suggestion ->
                        featureCatalog.supports(deviceType, suggestion.featureCode()))
                .filter(suggestion -> isSafeSource(suggestion.sourceUrl()))
                .limit(DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS)
                .forEach(suggestion -> unique.putIfAbsent(
                        suggestion.featureCode().trim().toUpperCase(Locale.ROOT),
                        suggestion));
        return List.copyOf(unique.values());
    }

    private ChecklistGenerationContext context(
            Long deviceModelId,
            DeviceType deviceType,
            String manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            Set<String> confirmedFeatures) {
        requireSupportedOs(deviceType, osFamily);
        Set<String> normalized = new LinkedHashSet<>();
        if (confirmedFeatures != null) {
            if (confirmedFeatures.stream()
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            confirmedFeatures.stream()
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .forEach(normalized::add);
        }
        if (normalized.size() > DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS
                || normalized.stream()
                        .anyMatch(code -> !featureCatalog.supports(deviceType, code))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new ChecklistGenerationContext(
                deviceModelId,
                deviceType,
                manufacturer,
                modelName,
                modelCode,
                osFamily,
                normalized);
    }

    private void requireSupportedOs(DeviceType deviceType, OsFamily osFamily) {
        if (osFamily == null) {
            throw new BusinessException(ErrorCode.CHECKLIST_OS_NOT_SUPPORTED);
        }
        if (deviceType == DeviceType.LAPTOP
                && osFamily != OsFamily.WINDOWS
                && osFamily != OsFamily.LINUX) {
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record BaseChecklist(int templateVersion, List<GeneratedChecklistItem> items) {
        private BaseChecklist {
            items = List.copyOf(items);
        }
    }
}
