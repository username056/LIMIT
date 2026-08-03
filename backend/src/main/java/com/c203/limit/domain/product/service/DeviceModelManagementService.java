package com.c203.limit.domain.product.service;

import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelRequest;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelDetailResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelSummaryResponse;
import com.c203.limit.domain.admin.dto.response.ChecklistResearchResponse;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.service.ModelChecklistResearchService;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateItemResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceModelRequest;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.entity.DeviceModelReviewStatus;
import com.c203.limit.domain.product.entity.Manufacturer;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceModelRequestRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceModelManagementService {

    private final DeviceModelRepository modelRepository;
    private final DeviceModelRequestRepository requestRepository;
    private final DeviceModelRequestService requestService;
    private final CategoryRepository categoryRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final DeviceCatalogRegistrar catalogRegistrar;
    private final ModelChecklistResearchService researchService;
    private final AdminActionLogRepository actionLogRepository;

    public DeviceModelManagementService(
            DeviceModelRepository modelRepository,
            DeviceModelRequestRepository requestRepository,
            DeviceModelRequestService requestService,
            CategoryRepository categoryRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            DeviceCatalogRegistrar catalogRegistrar,
            ModelChecklistResearchService researchService,
            AdminActionLogRepository actionLogRepository) {
        this.modelRepository = modelRepository;
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.catalogRegistrar = catalogRegistrar;
        this.researchService = researchService;
        this.actionLogRepository = actionLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminDeviceModelSummaryResponse> list(DeviceModelReviewStatus reviewStatus) {
        List<DeviceModel> models = reviewStatus == null
                ? modelRepository.findAllByOrderByCreatedAtDesc()
                : modelRepository.findByReviewStatusOrderByCreatedAtDesc(reviewStatus);
        return models.stream().map(this::summary).toList();
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
                baseItems,
                research,
                model.getCreatedAt(),
                model.getUpdatedAt());
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
        return detail(modelId);
    }

    private AdminDeviceModelSummaryResponse summary(DeviceModel model) {
        ChecklistResearchResponse research = researchService.latest(model.getId());
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
                research == null ? null : research.status(),
                research == null ? null : research.researchVersion(),
                model.getCreatedAt(),
                model.getUpdatedAt());
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
