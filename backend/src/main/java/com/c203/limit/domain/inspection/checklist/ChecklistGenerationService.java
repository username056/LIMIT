package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
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

    /**
     * confirmedFeatures 각각에 대응하는 체크리스트 항목 정의를 결정론적으로 돌려준다. AI 리서치나
     * 외부 호출이 전혀 없다 — 카탈로그/정책(DeviceChecklistFeatureCatalog, LaptopChecklistPolicy)만
     * 사용해서, 상품 수정 시 선택 기능을 반영하는 짧은 트랜잭션 안에서 안전하게 호출할 수 있다.
     * "추천 기능 목록을 새로 만드는" 책임은 generateForModel/generateSnapshotForModel에 남긴다.
     */
    public Map<String, GeneratedChecklistItem> resolveConfirmedFeatureItems(
            Long deviceModelId, Set<String> confirmedFeatures) {
        Category model = activeModel(deviceModelId);
        Set<String> normalized = normalizeFeatureCodes(model.getDeviceType(), confirmedFeatures);
        if (normalized.isEmpty()) {
            return Map.of();
        }
        List<GeneratedChecklistItem> items = additionalItems(model, normalized, 1);
        if (items.size() != normalized.size()) {
            // 카탈로그가 지원하지 않는 feature 코드가 섞여 있었다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return items.stream()
                .collect(java.util.stream.Collectors.toMap(
                        GeneratedChecklistItem::featureCode, item -> item));
    }

    private Set<String> normalizeFeatureCodes(DeviceType deviceType, Set<String> confirmedFeatures) {
        if (confirmedFeatures == null || confirmedFeatures.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : confirmedFeatures) {
            if (value == null || value.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            normalized.add(value.trim().toUpperCase(Locale.ROOT));
        }
        if (normalized.size() > DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS
                || normalized.stream().anyMatch(code -> !featureCatalog.supports(deviceType, code))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
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
        return generatePreview(
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
        if (context.confirmedFeatures().isEmpty()) return Optional.empty();

        ModelChecklistResearch research = findOrResearch(model, context);
        ChecklistSupplementResult supplement = usableSupplement(research);
        List<ChecklistSuggestion> suggestions =
                validSuggestions(context.deviceType(), supplement.suggestions());
        Set<String> suggestedCodes = suggestions.stream()
                .map(ChecklistSuggestion::featureCode)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (!suggestedCodes.containsAll(context.confirmedFeatures())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        BaseChecklist checklist = baseChecklist(model, context.confirmedFeatures());
        return Optional.of(new GeneratedChecklist(
                context.deviceModelId(),
                context.manufacturer(),
                context.modelName(),
                context.osFamily(),
                checklist.templateVersion(),
                true,
                checklist.items(),
                suggestions.stream()
                        .filter(suggestion -> context.confirmedFeatures().contains(
                                suggestion.featureCode().trim().toUpperCase(Locale.ROOT)))
                        .toList(),
                List.of(),
                research.getId(),
                research.getStatus().name()));
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

    /**
     * 기존 직접 입력 상품 등록 계약을 유지하기 위한 호환 경로다. 신규 판매 화면에서는 모델 검토
     * 요청을 사용하지만, 이미 직접 입력을 사용하는 클라이언트는 carrier 모델 ID로 스냅샷을 만들 수
     * 있다.
     */
    public GeneratedChecklist generateCustomForModel(
            Long carrierDeviceModelId,
            String manufacturer,
            String modelName,
            String modelCode,
            OsFamily osFamily,
            Set<String> confirmedFeatures) {
        Category carrier = activeModel(carrierDeviceModelId);
        if (carrier.getDeviceType() != DeviceType.LAPTOP) {
            throw new BusinessException(ErrorCode.CHECKLIST_DEVICE_TYPE_NOT_SUPPORTED);
        }
        if (isBlank(manufacturer) || isBlank(modelName) || osFamily == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ChecklistGenerationContext context = context(
                carrier.getId(),
                carrier.getDeviceType(),
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
                                item.visibleToBuyer(),
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

    private GeneratedChecklist generatePreview(
            ChecklistGenerationContext context,
            BaseChecklist baseChecklist,
            ModelChecklistResearch research) {
        ChecklistSupplementResult supplement = usableSupplement(research);
        List<ChecklistSuggestion> suggestions =
                validSuggestions(context.deviceType(), supplement.suggestions());
        return new GeneratedChecklist(
                context.deviceModelId(),
                context.manufacturer(),
                context.modelName(),
                context.osFamily(),
                baseChecklist.templateVersion(),
                false,
                baseChecklist.items(),
                suggestions,
                supplement.reviewCandidates().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .filter(value -> !featureCatalog.supports(context.deviceType(), value))
                        .distinct()
                        .limit(DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS)
                        .toList(),
                research == null ? null : research.getId(),
                research == null ? null : research.getStatus().name());
    }

    private ChecklistSupplementResult usableSupplement(ModelChecklistResearch research) {
        if (research == null
                || research.getResultJson() == null
                || (research.getStatus() != ModelChecklistResearchStatus.PENDING_REVIEW
                        && research.getStatus() != ModelChecklistResearchStatus.APPROVED)) {
            return ChecklistSupplementResult.unavailable();
        }
        try {
            return objectMapper.readValue(
                    research.getResultJson(), ChecklistSupplementResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize checklist research", exception);
        }
    }

    private ModelChecklistResearch findOrResearch(
            Category model, ChecklistGenerationContext context) {
        Optional<ModelChecklistResearch> existing = researchRepository
                .findFirstByDeviceModelIdOrderByResearchVersionDesc(model.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        ModelChecklistResearch research;
        try {
            research = researchRepository.saveAndFlush(
                    ModelChecklistResearch.start(model.getId(), 1, writeContext(context)));
        } catch (DataIntegrityViolationException exception) {
            return researchRepository
                    .findFirstByDeviceModelIdOrderByResearchVersionDesc(model.getId())
                    .orElseThrow(() -> exception);
        }

        ChecklistSupplementResult supplement;
        try {
            supplement = supplementClient.suggest(context);
        } catch (RuntimeException exception) {
            research.fail(writeSupplement(ChecklistSupplementResult.requestFailed()));
            return researchRepository.saveAndFlush(research);
        }
        if (!supplement.available()) {
            research.fail(writeSupplement(supplement));
            return researchRepository.saveAndFlush(research);
        }
        research.complete(writeSupplement(supplement));
        return researchRepository.saveAndFlush(research);
    }

    private String writeContext(ChecklistGenerationContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize checklist research input", exception);
        }
    }

    private String writeSupplement(ChecklistSupplementResult supplement) {
        try {
            return objectMapper.writeValueAsString(supplement);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize checklist research", exception);
        }
    }

    private BaseChecklist baseChecklist(Category model, Set<String> confirmedFeatures) {
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
        items.addAll(additionalItems(model, confirmedFeatures, items.size() + 1));
        for (int index = 0; index < items.size(); index++) {
            items.set(index, items.get(index).withDisplayOrder(index + 1));
        }
        return new BaseChecklist(template.getVersion(), List.copyOf(items));
    }

    private List<GeneratedChecklistItem> additionalItems(
            Category model, Set<String> confirmedFeatures, int firstDisplayOrder) {
        if (model.getDeviceType() == DeviceType.LAPTOP) {
            return laptopPolicy.additionalItems(
                    laptopFeatures(confirmedFeatures), firstDisplayOrder);
        }
        return featureCatalog.additionalItems(
                model.getDeviceType(), confirmedFeatures, firstDisplayOrder);
    }

    private BaseChecklist laptopChecklist(
            OsFamily osFamily, Set<String> confirmedFeatures) {
        return new BaseChecklist(
                1, laptopPolicy.generate(osFamily, laptopFeatures(confirmedFeatures)));
    }

    private Set<LaptopFeatureCode> laptopFeatures(Set<String> confirmedFeatures) {
        return confirmedFeatures.isEmpty()
                ? Set.of()
                : confirmedFeatures.stream()
                        .map(LaptopFeatureCode::valueOf)
                        .collect(java.util.stream.Collectors.toCollection(
                                () -> EnumSet.noneOf(LaptopFeatureCode.class)));
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
                item.isVisibleToBuyer(),
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
                        suggestion
                                .withEvidenceType(evidenceType(
                                        deviceType, suggestion.featureCode()))
                                .withItemCode(itemCode(
                                        deviceType, suggestion.featureCode()))));
        return List.copyOf(unique.values());
    }

    private EvidenceType evidenceType(DeviceType deviceType, String featureCode) {
        String normalized = featureCode.trim().toUpperCase(Locale.ROOT);
        if (deviceType == DeviceType.LAPTOP) {
            return laptopPolicy.evidenceType(LaptopFeatureCode.valueOf(normalized));
        }
        return featureCatalog
                .find(deviceType, normalized)
                .map(DeviceChecklistFeatureCatalog.FeatureDefinition::evidenceType)
                .orElseThrow();
    }

    /** 이 feature를 확정했을 때 실제로 생성되는 체크리스트 항목 코드. 카탈로그/정책에서 결정론적으로 파생한다. */
    private String itemCode(DeviceType deviceType, String featureCode) {
        String normalized = featureCode.trim().toUpperCase(Locale.ROOT);
        if (deviceType == DeviceType.LAPTOP) {
            return laptopPolicy.itemCode(LaptopFeatureCode.valueOf(normalized));
        }
        return featureCatalog
                .find(deviceType, normalized)
                .map(DeviceChecklistFeatureCatalog.FeatureDefinition::itemCode)
                .orElseThrow();
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
