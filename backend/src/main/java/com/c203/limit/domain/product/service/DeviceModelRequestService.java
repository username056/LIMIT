package com.c203.limit.domain.product.service;

import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelRequest;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.product.dto.request.CreateDeviceModelRequest;
import com.c203.limit.domain.product.dto.response.DeviceModelRequestResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceModelRequest;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRequestRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceModelRequestService {
    private static final Logger log = LoggerFactory.getLogger(DeviceModelRequestService.class);

    private final CategoryRepository categoryRepository;
    private final DeviceModelRequestRepository requestRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final AdminActionLogRepository actionLogRepository;
    private final DeviceCatalogRegistrar catalogRegistrar;

    public DeviceModelRequestService(
            CategoryRepository categoryRepository,
            DeviceModelRequestRepository requestRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            AdminActionLogRepository actionLogRepository,
            DeviceCatalogRegistrar catalogRegistrar) {
        this.categoryRepository = categoryRepository;
        this.requestRepository = requestRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.actionLogRepository = actionLogRepository;
        this.catalogRegistrar = catalogRegistrar;
    }

    @Transactional
    public DeviceModelRequestResponse create(
            Long memberId, CreateDeviceModelRequest request) {
        Category category = categoryRepository
                .findById(request.categoryId())
                .filter(item -> item.getParent() == null && item.isActive())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        if (category.getDeviceType() == null || request.osFamily() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (requestRepository
                .existsByParentCategoryIdAndManufacturerIgnoreCaseAndModelNameIgnoreCaseAndStatus(
                        category.getId(),
                        request.manufacturer().trim(),
                        request.modelName().trim(),
                        DeviceModelRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DEVICE_MODEL_REQUEST_DUPLICATED);
        }
        DeviceModelRequest created = requestRepository.saveAndFlush(DeviceModelRequest.create(
                memberId,
                category.getId(),
                request.manufacturer(),
                request.modelName(),
                request.modelCode(),
                request.osFamily()));
        Category model = provision(created, category);
        created.provision(model.getId());
        log.info(
                "device model request created: requestId={}, categoryId={}, status={}",
                created.getId(),
                category.getId(),
                created.getStatus());
        return DeviceModelRequestResponse.from(created);
    }

    @Transactional(readOnly = true)
    public List<DeviceModelRequestResponse> list(DeviceModelRequestStatus status) {
        DeviceModelRequestStatus filter =
                status == null ? DeviceModelRequestStatus.PENDING : status;
        return requestRepository.findByStatusOrderByCreatedAtAsc(filter).stream()
                .map(DeviceModelRequestResponse::from)
                .toList();
    }

    @Transactional
    public DeviceModelRequestResponse update(
            Long requestId, Long adminId, UpdateDeviceModelRequest update) {
        DeviceModelRequest request = pendingRequest(requestId);
        Category category =
                categoryRepository
                        .findById(update.categoryId())
                        .filter(item -> item.getParent() == null && item.isActive())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        String manufacturer = update.manufacturer().trim();
        String modelName = update.modelName().trim();
        if (requestRepository
                .existsByParentCategoryIdAndManufacturerIgnoreCaseAndModelNameIgnoreCaseAndStatusAndIdNot(
                        category.getId(),
                        manufacturer,
                        modelName,
                        DeviceModelRequestStatus.PENDING,
                        requestId)) {
            throw new BusinessException(ErrorCode.DEVICE_MODEL_REQUEST_DUPLICATED);
        }
        request.updateDetails(
                category.getId(),
                manufacturer,
                modelName,
                update.modelCode(),
                update.osFamily());
        if (request.getResolvedModelId() != null) {
            Category model = categoryRepository
                    .findById(request.getResolvedModelId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
            Long previousParentId = model.getParent() == null ? null : model.getParent().getId();
            model.updateLeaf(
                    category,
                    modelName,
                    manufacturer,
                    update.osFamily(),
                    update.modelCode());
            catalogRegistrar.update(model);
            if (!category.getId().equals(previousParentId)) {
                publishBaseTemplate(model, category, false);
            }
        }
        actionLogRepository.save(
                AdminActionLog.of(
                        adminId,
                        "DEVICE_MODEL_REQUEST_UPDATE",
                        "DEVICE_MODEL_REQUEST",
                        requestId,
                        "신규 기기 모델 요청 정보 수정"));
        log.info(
                "device model request updated: requestId={}, updatedByAdminId={}",
                requestId,
                adminId);
        return DeviceModelRequestResponse.from(request);
    }

    @Transactional
    public DeviceModelRequestResponse approve(Long requestId, Long adminId, String note) {
        DeviceModelRequest request = pendingRequest(requestId);
        if (request.getResolvedModelId() == null) {
            throw new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND);
        }
        request.approve(adminId, request.getResolvedModelId(), note);
        catalogRegistrar.completeReview(request.getResolvedModelId(), adminId, note);
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "DEVICE_MODEL_REQUEST_APPROVE",
                "DEVICE_MODEL_REQUEST",
                requestId,
                note));
        log.info(
                "device model request reviewed: requestId={}, modelId={}",
                requestId,
                request.getResolvedModelId());
        return DeviceModelRequestResponse.from(request);
    }

    @Transactional
    public DeviceModelRequestResponse reject(Long requestId, Long adminId, String note) {
        DeviceModelRequest request = pendingRequest(requestId);
        request.reject(adminId, note);
        if (request.getResolvedModelId() != null) {
            categoryRepository.findById(request.getResolvedModelId()).ifPresent(Category::deactivate);
            catalogRegistrar.deactivate(request.getResolvedModelId());
        }
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "DEVICE_MODEL_REQUEST_REJECT",
                "DEVICE_MODEL_REQUEST",
                requestId,
                note));
        log.info("device model request rejected: requestId={}", requestId);
        return DeviceModelRequestResponse.from(request);
    }

    private DeviceModelRequest pendingRequest(Long requestId) {
        DeviceModelRequest request = requestRepository
                .findByIdForUpdate(requestId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.DEVICE_MODEL_REQUEST_NOT_FOUND));
        if (request.getStatus() != DeviceModelRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.DEVICE_MODEL_REQUEST_STATE_CONFLICT);
        }
        return request;
    }

    private Category provision(DeviceModelRequest request, Category parent) {
        int displayOrder = categoryRepository
                        .findByParentIdOrderByDisplayOrderAsc(parent.getId())
                        .stream()
                        .mapToInt(Category::getDisplayOrder)
                        .max()
                        .orElse(0)
                + 1;
        String modelCode = request.getModelCode() == null
                ? "REQUEST-" + request.getId()
                : request.getModelCode();
        Category model = categoryRepository.saveAndFlush(Category.createLeaf(
                parent,
                request.getModelName(),
                parent.getDeviceType(),
                request.getManufacturer(),
                request.getOsFamily(),
                modelCode,
                List.of(),
                displayOrder));
        catalogRegistrar.registerReported(model, request.getRequestedByMemberId());
        publishBaseTemplate(model, parent, true);
        return model;
    }

    private void publishBaseTemplate(Category model, Category parent, boolean initial) {
        ChecklistTemplate sourceTemplate = sourceTemplate(parent.getId(), model.getId());
        List<ChecklistTemplateItem> sourceItems = templateItemRepository
                .findByChecklistTemplateIdOrderByDisplayOrderAsc(sourceTemplate.getId());
        int version = initial
                ? 1
                : templateRepository
                        .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                                model.getId(), ChecklistTemplateStatus.PUBLISHED)
                        .map(template -> template.getVersion() + 1)
                        .orElse(1);
        ChecklistTemplate template = templateRepository.saveAndFlush(
                ChecklistTemplate.createDraft(model.getId(), version));
        templateItemRepository.saveAllAndFlush(sourceItems.stream()
                .map(item -> ChecklistTemplateItem.createGenerated(
                        template,
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
        template.publish();
    }

    private ChecklistTemplate sourceTemplate(Long parentCategoryId, Long excludedModelId) {
        return categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(
                        parentCategoryId)
                .stream()
                .filter(category -> !category.getId().equals(excludedModelId))
                .map(Category::getId)
                .map(categoryId -> templateRepository
                        .findFirstByCategoryIdAndStatusOrderByVersionAsc(
                                categoryId, ChecklistTemplateStatus.PUBLISHED)
                        .orElse(null))
                .filter(template -> template != null)
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
    }
}
