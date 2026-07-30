package com.c203.limit.domain.product.service;

import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceModelRequestService {
    private final CategoryRepository categoryRepository;
    private final DeviceModelRequestRepository requestRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final AdminActionLogRepository actionLogRepository;

    public DeviceModelRequestService(
            CategoryRepository categoryRepository,
            DeviceModelRequestRepository requestRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            AdminActionLogRepository actionLogRepository) {
        this.categoryRepository = categoryRepository;
        this.requestRepository = requestRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.actionLogRepository = actionLogRepository;
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
        DeviceModelRequest created = requestRepository.save(DeviceModelRequest.create(
                memberId,
                category.getId(),
                request.manufacturer(),
                request.modelName(),
                request.modelCode(),
                request.osFamily()));
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
    public DeviceModelRequestResponse approve(Long requestId, Long adminId, String note) {
        DeviceModelRequest request = pendingRequest(requestId);
        Category parent = categoryRepository
                .findById(request.getParentCategoryId())
                .filter(category -> category.getParent() == null && category.isActive())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        ChecklistTemplate sourceTemplate = sourceTemplate(parent.getId());
        List<ChecklistTemplateItem> sourceItems = templateItemRepository
                .findByChecklistTemplateIdOrderByDisplayOrderAsc(sourceTemplate.getId());
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
        ChecklistTemplate template = templateRepository.saveAndFlush(
                ChecklistTemplate.createDraft(model.getId(), 1));
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
                        item.getDisplayOrder()))
                .toList());
        template.publish();
        request.approve(adminId, model.getId(), note);
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "DEVICE_MODEL_REQUEST_APPROVE",
                "DEVICE_MODEL_REQUEST",
                requestId,
                note));
        return DeviceModelRequestResponse.from(request);
    }

    @Transactional
    public DeviceModelRequestResponse reject(Long requestId, Long adminId, String note) {
        DeviceModelRequest request = pendingRequest(requestId);
        request.reject(adminId, note);
        actionLogRepository.save(AdminActionLog.of(
                adminId,
                "DEVICE_MODEL_REQUEST_REJECT",
                "DEVICE_MODEL_REQUEST",
                requestId,
                note));
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

    private ChecklistTemplate sourceTemplate(Long parentCategoryId) {
        return categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(
                        parentCategoryId)
                .stream()
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
