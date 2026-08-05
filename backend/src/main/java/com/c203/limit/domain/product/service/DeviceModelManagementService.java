package com.c203.limit.domain.product.service;

import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelRequest;
import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelStatusRequest;
import com.c203.limit.domain.admin.dto.response.AdminChecklistMaterialResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelDetailResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelImpactResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelSummaryResponse;
import com.c203.limit.domain.admin.dto.response.AdminProductMaterialsResponse;
import com.c203.limit.domain.admin.dto.response.AdminRelatedProductResponse;
import com.c203.limit.domain.admin.dto.response.ChecklistResearchResponse;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.service.ModelChecklistResearchService;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateItemResponse;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceModelRequest;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.entity.DeviceModelReviewStatus;
import com.c203.limit.domain.product.entity.Manufacturer;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceModelRequestRepository;
import com.c203.limit.domain.product.repository.DeviceModelListingCountProjection;
import com.c203.limit.domain.product.repository.DeviceVariantRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import jakarta.persistence.criteria.JoinType;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceModelManagementService {

    private static final Logger log =
            LoggerFactory.getLogger(DeviceModelManagementService.class);

    private final DeviceModelRepository modelRepository;
    private final DeviceModelRequestRepository requestRepository;
    private final DeviceModelRequestService requestService;
    private final CategoryRepository categoryRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final DeviceCatalogRegistrar catalogRegistrar;
    private final ModelChecklistResearchService researchService;
    private final AdminActionLogRepository actionLogRepository;
    private final ListingRepository listingRepository;
    private final DeviceVariantRepository variantRepository;
    private final ListingImageRepository imageRepository;
    private final ListingChecklistItemRepository listingChecklistItemRepository;
    private final EvidenceRepository evidenceRepository;
    private final MediaUrlResolver mediaUrlResolver;

    public DeviceModelManagementService(
            DeviceModelRepository modelRepository,
            DeviceModelRequestRepository requestRepository,
            DeviceModelRequestService requestService,
            CategoryRepository categoryRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            DeviceCatalogRegistrar catalogRegistrar,
            ModelChecklistResearchService researchService,
            AdminActionLogRepository actionLogRepository,
            ListingRepository listingRepository,
            DeviceVariantRepository variantRepository,
            ListingImageRepository imageRepository,
            ListingChecklistItemRepository listingChecklistItemRepository,
            EvidenceRepository evidenceRepository,
            MediaUrlResolver mediaUrlResolver) {
        this.modelRepository = modelRepository;
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.catalogRegistrar = catalogRegistrar;
        this.researchService = researchService;
        this.actionLogRepository = actionLogRepository;
        this.listingRepository = listingRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.listingChecklistItemRepository = listingChecklistItemRepository;
        this.evidenceRepository = evidenceRepository;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDeviceModelSummaryResponse> list(
            String keyword,
            Long categoryId,
            Long manufacturerId,
            Boolean isActive,
            DeviceModelReviewStatus reviewStatus,
            ModelChecklistResearchStatus researchStatus,
            Integer page,
            Integer size,
            String sort) {
        Set<Long> researchModelIds = researchStatus == null
                ? null
                : researchService.modelIdsByLatestStatus(researchStatus);
        if (researchModelIds != null && researchModelIds.isEmpty()) {
            return emptyPage(page, size);
        }
        Page<DeviceModel> result = modelRepository.findAll(
                specification(
                        keyword,
                        categoryId,
                        manufacturerId,
                        isActive,
                        reviewStatus,
                        researchModelIds),
                PageRequest.of(
                        normalizedPage(page),
                        normalizedSize(size),
                        modelSort(sort)));
        List<Long> modelIds = result.stream().map(DeviceModel::getId).toList();
        Map<Long, Long> productCounts = modelIds.isEmpty()
                ? Map.of()
                : listingRepository.countByDeviceModelIds(modelIds).stream()
                        .collect(Collectors.toMap(
                                DeviceModelListingCountProjection::getDeviceModelId,
                                DeviceModelListingCountProjection::getListingCount));
        Map<Long, ModelChecklistResearchService.LatestResearchSummary> researches =
                researchService.latestSummaries(Set.copyOf(modelIds));
        return new PageResponse<>(
                result.stream()
                        .map(model -> summary(
                                model,
                                researches.get(model.getId()),
                                productCounts.getOrDefault(model.getId(), 0L)))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public AdminDeviceModelDetailResponse detail(Long modelId) {
        DeviceModel model = modelRepository
                .findWithCatalogById(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        ChecklistResearchResponse research = researchService.latest(modelId);
        List<ChecklistTemplateItemResponse> baseItems = templateRepository
                .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        modelId, ChecklistTemplateStatus.PUBLISHED)
                .map(template -> templateItemRepository
                        .findByChecklistTemplateIdOrderByDisplayOrderAsc(template.getId())
                        .stream()
                        .map(this::templateItem)
                        .toList())
                .orElse(List.of());
        return new AdminDeviceModelDetailResponse(
                model.getId(),
                model.getCategory().getId(),
                model.getCategory().getName(),
                model.manufacturerName(),
                model.getModelName(),
                model.getModelCode(),
                model.getOsFamily() == null ? null : model.getOsFamily().name(),
                model.getReviewStatus().name(),
                model.getSourceType().name(),
                model.isActive(),
                model.getReportedByMemberId(),
                model.getReviewedByAdminId(),
                model.getReviewedAt(),
                model.getReviewNote(),
                model.getDisabledAt(),
                model.getDisabledByAdminId(),
                model.getDisableReason(),
                model.getReplacementModelId(),
                baseItems,
                research,
                impact(modelId),
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRelatedProductResponse> products(
            Long modelId, Integer page, Integer size, String sort) {
        requireModel(modelId);
        Page<Listing> result = listingRepository.findByDeviceModelIdAndDeletedAtIsNull(
                modelId,
                PageRequest.of(
                        normalizedPage(page),
                        normalizedSize(size),
                        productSort(sort)));
        return new PageResponse<>(
                result.stream().map(this::relatedProduct).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public PageResponse<ChecklistResearchResponse> researches(
            Long modelId, Integer page, Integer size) {
        requireModel(modelId);
        return researchService.history(modelId, page, size);
    }

    @Transactional(readOnly = true)
    public AdminProductMaterialsResponse materials(Long modelId, Long productId) {
        Listing listing = listingRepository
                .findByIdAndDeletedAtIsNull(productId)
                .filter(item -> modelId.equals(item.getDeviceModelId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        List<ListingImageResponse> images = imageRepository
                .findAllByListingIdOrderByDisplayOrderAscIdAsc(listing.getId())
                .stream()
                .map(image -> ListingImageResponse.of(
                        image, mediaUrlResolver.resolve(image.getS3Key(), image.getCdnUrl())))
                .toList();
        List<ListingChecklistItem> checklistItems = listingChecklistItemRepository
                .findAllByListingIdOrderByDisplayOrderAsc(listing.getId());
        Map<Long, List<Evidence>> evidenceByItem = evidenceRepository
                .findAllByListingId(listing.getId())
                .stream()
                .sorted(Comparator.comparing(Evidence::getUploadedAt).thenComparing(Evidence::getId))
                .collect(Collectors.groupingBy(
                        evidence -> evidence.getListingChecklistItem().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<AdminChecklistMaterialResponse> materials = checklistItems.stream()
                .map(item -> checklistMaterial(
                        item, evidenceByItem.getOrDefault(item.getId(), List.of())))
                .toList();
        return new AdminProductMaterialsResponse(listing.getId(), images, materials);
    }

    @Transactional
    public AdminDeviceModelDetailResponse updateStatus(
            Long modelId, Long adminId, UpdateDeviceModelStatusRequest request) {
        DeviceModel model = modelRepository
                .findByIdForUpdate(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        Category legacyModel = categoryRepository
                .findById(modelId)
                .filter(category -> category.getParent() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        if (Boolean.TRUE.equals(request.isActive())) {
            legacyModel.activate();
            model.activate(adminId, request.reason());
            actionLogRepository.save(AdminActionLog.of(
                    adminId,
                    "DEVICE_MODEL_ACTIVATE",
                    "DEVICE_MODEL",
                    modelId,
                    request.reason()));
            log.info("device model activated: modelId={}, adminId={}", modelId, adminId);
            return detail(modelId);
        }

        String reason = trimToNull(request.reason());
        if (reason == null || modelId.equals(request.replacementModelId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Long replacementModelId = request.replacementModelId();
        if (replacementModelId != null) {
            modelRepository
                    .findWithCatalogById(replacementModelId)
                    .filter(DeviceModel::isActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        }
        legacyModel.deactivate();
        model.deactivate(adminId, reason, replacementModelId);
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "DEVICE_MODEL_DISABLE",
                "DEVICE_MODEL",
                modelId,
                reason));
        log.info(
                "device model disabled: modelId={}, adminId={}, replacementModelId={}",
                modelId,
                adminId,
                replacementModelId);
        return detail(modelId);
    }

    @Transactional(readOnly = true)
    public AdminDeviceModelImpactResponse impact(Long modelId) {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ListingStatus status : ListingStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }
        listingRepository.countStatusesByDeviceModelId(modelId).forEach(count ->
                statusCounts.put(count.getStatus().name(), count.getListingCount()));
        long productCount = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        return new AdminDeviceModelImpactResponse(
                productCount,
                Map.copyOf(statusCounts),
                researchService.countByModelId(modelId),
                variantRepository.countByModelId(modelId));
    }

    @Transactional
    public AdminDeviceModelDetailResponse update(
            Long modelId, Long adminId, UpdateDeviceModelRequest update) {
        DeviceModelRequest report = requestRepository
                .findFirstByResolvedModelIdOrderByCreatedAtDesc(modelId)
                .filter(request -> request.getStatus() == DeviceModelRequestStatus.PENDING)
                .orElse(null);
        if (report != null) {
            requestService.update(report.getId(), adminId, update);
            requestService.approve(report.getId(), adminId, "관리자 모델 정보 수정 완료");
            log.info(
                    "reported device model review completed: modelId={}, adminId={}",
                    modelId,
                    adminId);
            return detail(modelId);
        }

        Category parent = categoryRepository
                .findById(update.categoryId())
                .filter(category -> category.getParent() == null && category.isActive())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        Category legacyModel = categoryRepository
                .findById(modelId)
                .filter(category -> category.getParent() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        Long previousCategoryId = legacyModel.getParent().getId();
        String modelCode = update.modelCode() == null || update.modelCode().isBlank()
                ? legacyModel.getModelCode()
                : update.modelCode().trim();
        Long manufacturerId = Manufacturer.idOf(update.manufacturer());
        if (modelRepository.existsByManufacturerIdAndModelCodeAndIdNot(
                manufacturerId, modelCode, modelId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        legacyModel.updateLeaf(
                parent,
                update.modelName(),
                update.manufacturer(),
                update.osFamily(),
                modelCode);
        catalogRegistrar.update(legacyModel);
        catalogRegistrar.completeReview(modelId, adminId, "관리자 모델 정보 수정 완료");
        if (!previousCategoryId.equals(parent.getId())) {
            publishCorrectedBaseTemplate(legacyModel, parent);
        }
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "DEVICE_MODEL_UPDATE",
                "DEVICE_MODEL",
                modelId,
                "관리자 모델 정보 수정 완료"));
        log.info(
                "device model update completed: modelId={}, categoryId={}, adminId={}",
                modelId,
                parent.getId(),
                adminId);
        return detail(modelId);
    }

    private AdminDeviceModelSummaryResponse summary(
            DeviceModel model,
            ModelChecklistResearchService.LatestResearchSummary research,
            long relatedProductCount) {
        return new AdminDeviceModelSummaryResponse(
                model.getId(),
                model.getCategory().getId(),
                model.getCategory().getName(),
                model.manufacturerName(),
                model.getModelName(),
                model.getModelCode(),
                model.getOsFamily() == null ? null : model.getOsFamily().name(),
                model.getReviewStatus().name(),
                model.getSourceType().name(),
                model.isActive(),
                model.getReportedByMemberId(),
                research == null ? null : research.status().name(),
                research == null ? null : research.researchVersion(),
                relatedProductCount,
                model.getCreatedAt(),
                model.getUpdatedAt());
    }

    private AdminRelatedProductResponse relatedProduct(Listing listing) {
        return new AdminRelatedProductResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getTitle(),
                listing.getPrice(),
                listing.getStatus().name(),
                listing.getChecklistTemplateId(),
                listing.isPrecheckCompleted(),
                listing.getCreatedAt(),
                listing.getUpdatedAt());
    }

    private AdminChecklistMaterialResponse checklistMaterial(
            ListingChecklistItem item, List<Evidence> evidenceHistory) {
        Long latestId = evidenceHistory.isEmpty()
                ? null
                : evidenceHistory.get(evidenceHistory.size() - 1).getId();
        List<EvidenceResponse> evidence = java.util.stream.IntStream
                .range(0, evidenceHistory.size())
                .mapToObj(index -> evidence(
                        evidenceHistory.get(index), index + 1, latestId))
                .toList();
        return new AdminChecklistMaterialResponse(
                item.getId(),
                item.getItemCode(),
                item.getName(),
                item.getEvidenceType().name(),
                item.getCompletionStatus().name(),
                item.isRequired(),
                evidence);
    }

    private EvidenceResponse evidence(Evidence evidence, int attemptNo, Long latestId) {
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getListingChecklistItem().getId(),
                evidence.getEvidenceType().name(),
                attemptNo,
                evidence.getId().equals(latestId),
                mediaUrlResolver.resolve(evidence.getS3Key(), evidence.getCdnUrl()),
                evidence.getProcessingStatus().name(),
                "NONE",
                evidence.getCapturedAt() == null
                        ? null
                        : evidence.getCapturedAt().atOffset(ZoneOffset.UTC),
                evidence.getUploadedAt().atOffset(ZoneOffset.UTC));
    }

    private Specification<DeviceModel> specification(
            String keyword,
            Long categoryId,
            Long manufacturerId,
            Boolean isActive,
            DeviceModelReviewStatus reviewStatus,
            Set<Long> researchModelIds) {
        Specification<DeviceModel> specification = (root, query, builder) -> builder.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = DeviceModel.normalizeModelName(keyword);
            String keywordPattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            String normalizedPattern = "%" + normalizedKeyword + "%";
            specification = specification.and((root, query, builder) -> {
                var manufacturer = root.join("manufacturer", JoinType.LEFT);
                return builder.or(
                        builder.like(builder.lower(root.get("modelName")), keywordPattern),
                        builder.like(root.get("normalizedModelName"), normalizedPattern),
                        builder.like(builder.lower(root.get("modelCode")), keywordPattern),
                        builder.like(builder.lower(manufacturer.get("name")), keywordPattern));
            });
        }
        if (categoryId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("category").get("id"), categoryId));
        }
        if (manufacturerId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("manufacturer").get("id"), manufacturerId));
        }
        if (isActive != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("isActive"), isActive));
        }
        if (reviewStatus != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("reviewStatus"), reviewStatus));
        }
        if (researchModelIds != null) {
            specification = specification.and((root, query, builder) ->
                    root.get("id").in(researchModelIds));
        }
        return specification;
    }

    private Sort modelSort(String value) {
        String sort = value == null ? "" : value.trim();
        if ("createdAt,desc".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        if ("modelName,asc".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.asc("modelName"), Sort.Order.asc("id"));
        }
        return Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    }

    private Sort productSort(String value) {
        if ("createdAt,desc".equalsIgnoreCase(value == null ? "" : value.trim())) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        return Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    }

    private int normalizedPage(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    private int normalizedSize(Integer size) {
        return size == null ? 20 : Math.min(100, Math.max(1, size));
    }

    private PageResponse<AdminDeviceModelSummaryResponse> emptyPage(
            Integer page, Integer size) {
        return new PageResponse<>(
                List.of(), normalizedPage(page), normalizedSize(size), 0, 0, false);
    }

    private void requireModel(Long modelId) {
        if (!modelRepository.existsById(modelId)) {
            throw new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void publishCorrectedBaseTemplate(Category model, Category parent) {
        ChecklistTemplate source = categoryRepository
                .findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parent.getId())
                .stream()
                .filter(candidate -> !candidate.getId().equals(model.getId()))
                .map(Category::getId)
                .map(id -> templateRepository
                        .findFirstByCategoryIdAndStatusOrderByVersionAsc(
                                id, ChecklistTemplateStatus.PUBLISHED)
                        .orElse(null))
                .filter(template -> template != null)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
        int nextVersion = templateRepository
                        .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                                model.getId(), ChecklistTemplateStatus.PUBLISHED)
                        .map(template -> template.getVersion() + 1)
                        .orElse(1);
        ChecklistTemplate target = templateRepository.saveAndFlush(
                ChecklistTemplate.createDraft(model.getId(), nextVersion));
        List<ChecklistTemplateItem> sourceItems = templateItemRepository
                .findByChecklistTemplateIdOrderByDisplayOrderAsc(source.getId());
        templateItemRepository.saveAllAndFlush(sourceItems.stream()
                .map(item -> ChecklistTemplateItem.createGenerated(
                        target,
                        item.getItemCode(),
                        item.getName(),
                        item.getPurpose(),
                        item.getCaptureGuide(),
                        item.getEvidenceType(),
                        item.getAutomationType(),
                        item.getParserType(),
                        item.isRequired(),
                        item.isVisibleToBuyer(),
                        item.getDisplayOrder()))
                .toList());
        target.publish();
    }

    private ChecklistTemplateItemResponse templateItem(ChecklistTemplateItem item) {
        return new ChecklistTemplateItemResponse(
                item.getItemCode(),
                item.getName(),
                item.getEvidenceType().name(),
                item.isRequired(),
                item.getMinCount(),
                item.getMaxCount(),
                item.getMinDurationSec(),
                item.getMaxDurationSec(),
                item.getCaptureGuide(),
                true,
                item.isVisibleToBuyer(),
                item.isPrivacyMaskingRequired());
    }
}
