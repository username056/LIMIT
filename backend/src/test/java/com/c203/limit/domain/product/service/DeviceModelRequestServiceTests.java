package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelRequest;
import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceModelRequest;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRequestRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeviceModelRequestServiceTests {
    @Test
    void adminCannotMovePendingRequestToUnknownCategory() {
        CategoryRepository categories = mock(CategoryRepository.class);
        DeviceModelRequestRepository requests = mock(DeviceModelRequestRepository.class);
        ChecklistTemplateRepository templates = mock(ChecklistTemplateRepository.class);
        ChecklistTemplateItemRepository templateItems =
                mock(ChecklistTemplateItemRepository.class);
        AdminActionLogRepository logs = mock(AdminActionLogRepository.class);
        DeviceCatalogRegistrar catalogRegistrar = mock(DeviceCatalogRegistrar.class);
        DeviceModelRequestService service =
                new DeviceModelRequestService(
                        categories, requests, templates, templateItems, logs, catalogRegistrar);
        DeviceModelRequest request =
                DeviceModelRequest.create(
                        7L, 1L, "Samsung", "Galaxy S25", "SM-S931N", OsFamily.ANDROID);
        ReflectionTestUtils.setField(request, "id", 501L);
        when(requests.findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(categories.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        501L,
                                        9L,
                                        new UpdateDeviceModelRequest(
                                                999L,
                                                "Samsung",
                                                "Galaxy S25",
                                                "SM-S931N",
                                                OsFamily.ANDROID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void adminCanCorrectPendingRequestBeforeApproval() {
        CategoryRepository categories = mock(CategoryRepository.class);
        DeviceModelRequestRepository requests = mock(DeviceModelRequestRepository.class);
        ChecklistTemplateRepository templates = mock(ChecklistTemplateRepository.class);
        ChecklistTemplateItemRepository templateItems =
                mock(ChecklistTemplateItemRepository.class);
        AdminActionLogRepository logs = mock(AdminActionLogRepository.class);
        DeviceCatalogRegistrar catalogRegistrar = mock(DeviceCatalogRegistrar.class);
        DeviceModelRequestService service =
                new DeviceModelRequestService(
                        categories, requests, templates, templateItems, logs, catalogRegistrar);
        DeviceModelRequest request =
                DeviceModelRequest.create(
                        7L, 1L, "Samsnug", "Galxy S25", "SM-S931", OsFamily.ANDROID);
        ReflectionTestUtils.setField(request, "id", 501L);
        Category correctedParent =
                Category.createTopLevel("노트북", DeviceType.LAPTOP, 2);
        ReflectionTestUtils.setField(correctedParent, "id", 2L);
        when(requests.findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(categories.findById(2L)).thenReturn(Optional.of(correctedParent));

        var result =
                service.update(
                        501L,
                        9L,
                        new UpdateDeviceModelRequest(
                                2L,
                                "Samsung",
                                "Galaxy S25",
                                "SM-S931N",
                                OsFamily.ANDROID));

        assertThat(result.categoryId()).isEqualTo(2L);
        assertThat(result.manufacturer()).isEqualTo("Samsung");
        assertThat(result.modelName()).isEqualTo("Galaxy S25");
        assertThat(result.modelCode()).isEqualTo("SM-S931N");
        verify(logs).save(any());
    }

    @Test
    void approvalCreatesCatalogModelAndPublishedTemplate() {
        CategoryRepository categories = mock(CategoryRepository.class);
        DeviceModelRequestRepository requests = mock(DeviceModelRequestRepository.class);
        ChecklistTemplateRepository templates = mock(ChecklistTemplateRepository.class);
        ChecklistTemplateItemRepository templateItems =
                mock(ChecklistTemplateItemRepository.class);
        AdminActionLogRepository logs = mock(AdminActionLogRepository.class);
        DeviceCatalogRegistrar catalogRegistrar = mock(DeviceCatalogRegistrar.class);
        DeviceModelRequestService service = new DeviceModelRequestService(
                categories, requests, templates, templateItems, logs, catalogRegistrar);

        Category parent =
                Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(parent, "id", 1L);
        Category sourceModel = Category.createLeaf(
                parent,
                "Galaxy S24",
                DeviceType.SMARTPHONE,
                "Samsung",
                OsFamily.ANDROID,
                "SM-S921N",
                List.of(256),
                1);
        ReflectionTestUtils.setField(sourceModel, "id", 101L);
        DeviceModelRequest request =
                DeviceModelRequest.create(7L, 1L, "Samsung", "Galaxy S25", null, OsFamily.ANDROID);
        ReflectionTestUtils.setField(request, "id", 501L);
        ChecklistTemplate sourceTemplate = ChecklistTemplate.createDraft(101L, 1);
        ReflectionTestUtils.setField(sourceTemplate, "id", 301L);
        sourceTemplate.publish();
        ChecklistTemplateItem sourceItem = ChecklistTemplateItem.create(
                sourceTemplate,
                "EXT-001",
                "외관",
                "외관 확인",
                "기기를 촬영하세요.",
                EvidenceType.PHOTO,
                AutomationType.NONE,
                true,
                1);

        when(requests.findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(categories.findById(1L)).thenReturn(Optional.of(parent));
        when(categories.findByParentIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sourceModel));
        when(categories.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sourceModel));
        when(templates.findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(templateItems.findByChecklistTemplateIdOrderByDisplayOrderAsc(301L))
                .thenReturn(List.of(sourceItem));
        when(categories.saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category model = invocation.getArgument(0);
                    ReflectionTestUtils.setField(model, "id", 102L);
                    return model;
                });
        when(templates.saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> {
                    ChecklistTemplate template = invocation.getArgument(0);
                    ReflectionTestUtils.setField(template, "id", 302L);
                    return template;
                });

        var result = service.approve(501L, 9L, "공식 모델 확인");

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.resolvedCategoryId()).isEqualTo(102L);
        verify(templateItems).saveAllAndFlush(any());
        verify(logs).save(any());
    }
}
