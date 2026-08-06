package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.admin.dto.response.ChecklistResearchResponse;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.checklist.ChecklistGenerationContext;
import com.c203.limit.domain.inspection.checklist.ChecklistSupplementClient;
import com.c203.limit.domain.inspection.checklist.ChecklistSuggestion;
import com.c203.limit.domain.inspection.checklist.ChecklistSupplementResult;
import com.c203.limit.domain.inspection.checklist.DeviceChecklistFeatureCatalog;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklistItem;
import com.c203.limit.domain.inspection.checklist.LaptopChecklistPolicy;
import com.c203.limit.domain.inspection.checklist.LaptopFeatureCode;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.ModelChecklistResearchRepository;
import com.c203.limit.domain.product.dto.response.ChecklistSuggestionResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ModelChecklistResearchService {
    private static final Logger log =
            LoggerFactory.getLogger(ModelChecklistResearchService.class);

    private final ModelChecklistResearchRepository researchRepository;
    private final CategoryRepository categoryRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final AdminActionLogRepository actionLogRepository;
    private final LaptopChecklistPolicy laptopPolicy;
    private final DeviceChecklistFeatureCatalog featureCatalog;
    private final ChecklistSupplementClient supplementClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ModelChecklistResearchService(
            ModelChecklistResearchRepository researchRepository,
            CategoryRepository categoryRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            AdminActionLogRepository actionLogRepository,
            LaptopChecklistPolicy laptopPolicy,
            DeviceChecklistFeatureCatalog featureCatalog,
            ChecklistSupplementClient supplementClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.researchRepository = researchRepository;
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.actionLogRepository = actionLogRepository;
        this.laptopPolicy = laptopPolicy;
        this.featureCatalog = featureCatalog;
        this.supplementClient = supplementClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<ChecklistResearchResponse> list(ModelChecklistResearchStatus status) {
        List<ModelChecklistResearch> researches = status == null
                ? researchRepository.findAllByOrderByCreatedAtDesc()
                : researchRepository.findByStatusOrderByCreatedAtAsc(status);
        return researches.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ChecklistResearchResponse latest(Long modelId) {
        return researchRepository
                .findFirstByDeviceModelIdOrderByResearchVersionDesc(modelId)
                .map(this::response)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, LatestResearchSummary> latestSummaries(Set<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LatestResearchSummary> summaries = new LinkedHashMap<>();
        researchRepository.findLatestSummaries(modelIds).forEach(research -> summaries.put(
                research.getDeviceModelId(),
                new LatestResearchSummary(research.getStatus(), research.getResearchVersion())));
        return Map.copyOf(summaries);
    }

    @Transactional(readOnly = true)
    public Set<Long> modelIdsByLatestStatus(ModelChecklistResearchStatus status) {
        if (status == null) {
            return Set.of();
        }
        return Set.copyOf(researchRepository.findDeviceModelIdsByLatestStatus(status.name()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChecklistResearchResponse> history(
            Long modelId, Integer page, Integer size) {
        Category model = categoryRepository
                .findById(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        Page<ModelChecklistResearch> result = researchRepository.findByDeviceModelId(
                modelId,
                PageRequest.of(
                        page == null ? 0 : Math.max(0, page),
                        size == null ? 20 : Math.min(100, Math.max(1, size)),
                        Sort.by(Sort.Direction.DESC, "researchVersion")));
        return new PageResponse<>(
                result.stream().map(research -> response(research, model)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public long countByModelId(Long modelId) {
        return researchRepository.countByDeviceModelId(modelId);
    }

    @Transactional
    public ChecklistResearchResponse approve(
            Long researchId, Long adminId, Set<String> approvedFeatureCodes, String note) {
        ModelChecklistResearch research = pendingResearch(researchId);
        Category model = activeModel(research.getDeviceModelId());
        ChecklistSupplementResult supplement = readSupplement(research);
        List<ChecklistSuggestion> validSuggestions =
                validSuggestions(model.getDeviceType(), supplement.suggestions());
        Set<String> approvedCodes = approvedCodes(validSuggestions, approvedFeatureCodes);
        research.approve(adminId, null, note);
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "CHECKLIST_RESEARCH_APPROVE",
                "MODEL_CHECKLIST_RESEARCH",
                researchId,
                note));
        log.info(
                "checklist research reviewed: researchId={}, modelId={}, reviewedFeatureCount={}",
                researchId,
                model.getId(),
                approvedCodes.size());
        return response(research);
    }

    @Transactional
    public ChecklistResearchResponse reject(Long researchId, Long adminId, String note) {
        ModelChecklistResearch research = pendingResearch(researchId);
        research.reject(adminId, note);
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "CHECKLIST_RESEARCH_REJECT",
                "MODEL_CHECKLIST_RESEARCH",
                researchId,
                note));
        log.info("checklist research rejected: researchId={}", researchId);
        return response(research);
    }

    public ChecklistResearchResponse retry(Long researchId, Long adminId) {
        ModelChecklistResearch failed = researchRepository
                .findById(researchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_RESEARCH_NOT_FOUND));
        if (failed.getStatus() != ModelChecklistResearchStatus.FAILED) {
            throw new BusinessException(ErrorCode.CHECKLIST_RESEARCH_STATE_CONFLICT);
        }
        return researchModel(failed.getDeviceModelId(), adminId);
    }

    public ChecklistResearchResponse researchModel(Long modelId, Long adminId) {
        RetryTarget target = Objects.requireNonNull(
                transactionTemplate.execute(status -> beginResearch(modelId, adminId)));

        ChecklistSupplementResult supplement;
        try {
            supplement = supplementClient.suggest(target.context());
        } catch (RuntimeException exception) {
            supplement = ChecklistSupplementResult.requestFailed();
        }

        ChecklistSupplementResult retryResult = supplement;
        return Objects.requireNonNull(
                transactionTemplate.execute(
                        status -> finishResearch(target.researchId(), retryResult)));
    }

    private RetryTarget beginResearch(Long modelId, Long adminId) {
        Category model = activeModel(modelId);
        int nextVersion = researchRepository
                        .findFirstByDeviceModelIdOrderByResearchVersionDesc(modelId)
                        .map(research -> research.getResearchVersion() + 1)
                        .orElse(1);
        ChecklistGenerationContext context = new ChecklistGenerationContext(
                model.getId(),
                model.getDeviceType(),
                model.getManufacturer(),
                model.getName(),
                model.getModelCode(),
                model.getOsFamily(),
                Set.of());
        ModelChecklistResearch research = researchRepository.saveAndFlush(
                ModelChecklistResearch.start(modelId, nextVersion, writeContext(context)));
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "CHECKLIST_RESEARCH_RETRY",
                "MODEL_CHECKLIST_RESEARCH",
                research.getId(),
                "모델 AI 재조사"));
        log.info(
                "checklist research started: researchId={}, modelId={}, version={}",
                research.getId(),
                modelId,
                nextVersion);
        return new RetryTarget(research.getId(), context);
    }

    private ChecklistResearchResponse finishResearch(
            Long researchId, ChecklistSupplementResult supplement) {
        ModelChecklistResearch research = researchRepository
                .findByIdForUpdate(researchId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CHECKLIST_RESEARCH_NOT_FOUND));
        if (research.getStatus() != ModelChecklistResearchStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.CHECKLIST_RESEARCH_STATE_CONFLICT);
        }
        if (supplement.available()) {
            research.complete(writeSupplement(supplement));
            log.info("checklist research retry completed: researchId={}", researchId);
        } else {
            research.fail(writeSupplement(supplement));
            log.warn(
                    "checklist research retry failed: researchId={}, failureCode={}",
                    researchId,
                    supplement.failureCode());
        }
        researchRepository.saveAndFlush(research);
        return response(research);
    }

    private ModelChecklistResearch pendingResearch(Long researchId) {
        ModelChecklistResearch research = researchRepository
                .findByIdForUpdate(researchId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CHECKLIST_RESEARCH_NOT_FOUND));
        if (research.getStatus() != ModelChecklistResearchStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.CHECKLIST_RESEARCH_STATE_CONFLICT);
        }
        return research;
    }

    private Category activeModel(Long categoryId) {
        return categoryRepository
                .findById(categoryId)
                .filter(Category::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    private Set<String> approvedCodes(
            List<ChecklistSuggestion> suggestions, Set<String> requestedCodes) {
        Set<String> suggestedCodes = suggestions.stream()
                .map(ChecklistSuggestion::featureCode)
                .map(ModelChecklistResearchService::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedCodes == null) {
            return suggestedCodes;
        }
        if (requestedCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Set<String> approved = requestedCodes.stream()
                .map(ModelChecklistResearchService::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!suggestedCodes.containsAll(approved)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return approved;
    }

    private List<GeneratedChecklistItem> approvedItems(
            Category model, Set<String> approvedCodes) {
        if (model.getDeviceType() == DeviceType.LAPTOP) {
            Set<LaptopFeatureCode> features = approvedCodes.stream()
                    .map(LaptopFeatureCode::valueOf)
                    .collect(java.util.stream.Collectors.toCollection(
                            () -> EnumSet.noneOf(LaptopFeatureCode.class)));
            return laptopPolicy.generate(model.getOsFamily(), features);
        }

        ChecklistTemplate current = templateRepository
                .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        model.getId(), ChecklistTemplateStatus.PUBLISHED)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
        List<GeneratedChecklistItem> items = new ArrayList<>(templateItemRepository
                .findByChecklistTemplateIdOrderByDisplayOrderAsc(current.getId())
                .stream()
                .map(this::generatedItem)
                .toList());
        items.addAll(featureCatalog.additionalItems(
                model.getDeviceType(), approvedCodes, items.size() + 1));
        for (int index = 0; index < items.size(); index++) {
            items.set(index, items.get(index).withDisplayOrder(index + 1));
        }
        return List.copyOf(items);
    }

    private GeneratedChecklistItem generatedItem(ChecklistTemplateItem item) {
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

    private List<ChecklistSuggestion> validSuggestions(
            DeviceType deviceType, List<ChecklistSuggestion> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        return suggestions.stream()
                .filter(suggestion ->
                        suggestion != null
                                && suggestion.featureCode() != null
                                && !suggestion.featureCode().isBlank())
                .filter(suggestion ->
                        featureCatalog.supports(deviceType, suggestion.featureCode()))
                .filter(suggestion -> isSafeSource(suggestion.sourceUrl()))
                .limit(DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS)
                .toList();
    }

    private ChecklistResearchResponse response(ModelChecklistResearch research) {
        Category model = categoryRepository
                .findById(research.getDeviceModelId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        return response(research, model);
    }

    private ChecklistResearchResponse response(
            ModelChecklistResearch research, Category model) {
        ChecklistSupplementResult supplement = research.getResultJson() == null
                ? ChecklistSupplementResult.unavailable()
                : readSupplement(research);
        return new ChecklistResearchResponse(
                research.getId(),
                model.getId(),
                model.getDeviceType().name(),
                model.getManufacturer(),
                model.getName(),
                research.getResearchVersion(),
                research.getStatus().name(),
                validSuggestions(model.getDeviceType(), supplement.suggestions()).stream()
                        .map(ChecklistSuggestionResponse::from)
                        .toList(),
                supplement.reviewCandidates(),
                supplement.failureCode(),
                supplement.failureMessage(),
                research.getPublishedTemplateId(),
                research.getReviewedByAdminId(),
                research.getReviewNote(),
                research.getCreatedAt(),
                research.getUpdatedAt());
    }

    private ChecklistSupplementResult readSupplement(ModelChecklistResearch research) {
        try {
            return objectMapper.readValue(
                    research.getResultJson(), ChecklistSupplementResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to read checklist research", exception);
        }
    }

    private String writeSupplement(ChecklistSupplementResult supplement) {
        try {
            return objectMapper.writeValueAsString(supplement);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize checklist research", exception);
        }
    }

    private String writeContext(ChecklistGenerationContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize checklist research input", exception);
        }
    }

    private record RetryTarget(Long researchId, ChecklistGenerationContext context) {}

    public record LatestResearchSummary(
            ModelChecklistResearchStatus status, int researchVersion) {}

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

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
