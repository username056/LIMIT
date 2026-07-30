package com.c203.limit.domain.product.service;

import com.c203.limit.domain.inspection.entity.AccountRemovalGuide;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.repository.AccountRemovalGuideRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateItemResponse;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelDetailResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelSummaryResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideStepResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalogService {
    private static final Logger log = LoggerFactory.getLogger(ProductCatalogService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryRepository categoryRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final AccountRemovalGuideRepository guideRepository;

    public ProductCatalogService(
            CategoryRepository categoryRepository,
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            AccountRemovalGuideRepository guideRepository) {
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.guideRepository = guideRepository;
    }

    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> categories(Long parentId, boolean activeOnly) {
        List<Category> categories;
        if (parentId == null) {
            categories = activeOnly
                    ? categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc()
                    : categoryRepository.findByParentIsNullOrderByDisplayOrderAsc();
        } else {
            categories = activeOnly
                    ? categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parentId)
                    : categoryRepository.findByParentIdOrderByDisplayOrderAsc(parentId);
        }
        return categories.stream().map(this::category).toList();
    }

    @Transactional(readOnly = true)
    public ModelPage models(
            Long categoryId, Long manufacturerId, String keyword, int page, int size) {
        validatePage(page, size);
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<Category> result = categoryRepository.findModels(
                categoryId, manufacturerId, normalized, PageRequest.of(page, size));
        return new ModelPage(
                result.getContent().stream().map(this::modelSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public DeviceModelDetailResponse model(Long modelId) {
        Category model = findModel(modelId);
        ChecklistTemplate template = publishedTemplate(modelId);
        return new DeviceModelDetailResponse(
                model.getId(),
                model.getManufacturer(),
                model.getParent() == null ? model.getName() : model.getParent().getName(),
                model.getModelCode(),
                model.getName(),
                model.getOsFamily() == null ? null : model.getOsFamily().name(),
                storage(model.getSupportedStorageGb()),
                template.getVersion(),
                guideRepository
                        .findFirstByDeviceTypeAndManufacturerOrderByTemplateVersionDesc(
                                model.getDeviceType(), model.getManufacturer())
                        .isPresent());
    }

    @Transactional(readOnly = true)
    public ChecklistTemplateResponse checklistTemplate(Long modelId) {
        findModel(modelId);
        ChecklistTemplate template = publishedTemplate(modelId);
        List<ChecklistTemplateItemResponse> items = templateItemRepository
                .findByChecklistTemplateIdOrderByDisplayOrderAsc(template.getId())
                .stream()
                .map(this::templateItem)
                .toList();
        return new ChecklistTemplateResponse(
                template.getId(), modelId, template.getVersion(), items);
    }

    @Transactional(readOnly = true)
    public HandoverGuideResponse handoverGuide(Long modelId) {
        Category model = findModel(modelId);
        AccountRemovalGuide guide = guideRepository
                .findFirstByDeviceTypeAndManufacturerOrderByTemplateVersionDesc(
                        model.getDeviceType(), model.getManufacturer())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
        List<HandoverGuideStepResponse> steps = guide.getSteps().stream()
                .map(
                        step ->
                                new HandoverGuideStepResponse(
                                        guide.getSteps().indexOf(step) + 1,
                                        "STEP_" + (guide.getSteps().indexOf(step) + 1),
                                        step,
                                        step,
                                        true))
                .toList();
        return new HandoverGuideResponse(
                guide.getId(),
                modelId,
                guide.getTemplateVersion(),
                model.getName() + " 판매 준비",
                steps,
                guide.getDisclaimerText());
    }

    private Category findModel(Long modelId) {
        return categoryRepository
                .findById(modelId)
                .filter(category -> category.getModelCode() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_MODEL_NOT_FOUND));
    }

    private ChecklistTemplate publishedTemplate(Long modelId) {
        return templateRepository
                .findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        modelId, ChecklistTemplateStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
    }

    private DeviceCategoryResponse category(Category category) {
        return new DeviceCategoryResponse(
                category.getId(),
                category.getDeviceType().name(),
                category.getName(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.isActive());
    }

    private DeviceModelSummaryResponse modelSummary(Category model) {
        return new DeviceModelSummaryResponse(
                model.getId(),
                model.getManufacturerId(),
                model.getManufacturer(),
                model.getParent() == null ? model.getId() : model.getParent().getId(),
                model.getModelCode(),
                model.getName(),
                model.getOsFamily() == null ? null : model.getOsFamily().name(),
                model.isActive());
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

    private List<Integer> storage(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .map(Integer::valueOf)
                    .toList();
        } catch (NumberFormatException exception) {
            log.warn("invalid supported storage catalog value ignored");
            return List.of();
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public record ModelPage(
            List<DeviceModelSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
