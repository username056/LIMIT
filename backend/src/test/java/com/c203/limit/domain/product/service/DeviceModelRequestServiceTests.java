package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.c203.limit.domain.product.dto.request.CreateDeviceModelRequest;
import com.c203.limit.domain.product.dto.response.DeviceModelRequestResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceModelRequest;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRequestRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DeviceModelRequestServiceTests {
    @Test
    void createImmediatelyProvisionsModelAndPublishedBaseTemplate() {
        CategoryRepository categories = mock(CategoryRepository.class);
        DeviceModelRequestRepository requests = mock(DeviceModelRequestRepository.class);
        ChecklistTemplateRepository templates = mock(ChecklistTemplateRepository.class);
        ChecklistTemplateItemRepository templateItems =
                mock(ChecklistTemplateItemRepository.class);
        AdminActionLogRepository logs = mock(AdminActionLogRepository.class);
        DeviceCatalogRegistrar catalogRegistrar = mock(DeviceCatalogRegistrar.class);
        DeviceModelRequestService service = new DeviceModelRequestService(
                categories, requests, templates, templateItems, logs, catalogRegistrar);

        Category parent = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 1);
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

        when(categories.findById(1L)).thenReturn(Optional.of(parent));
        when(requests.existsByParentCategoryIdAndManufacturerIgnoreCaseAndModelNameIgnoreCaseAndStatus(
                        1L, "Samsung", "Galaxy S25", com.c203.limit.domain.product.entity.DeviceModelRequestStatus.PENDING))
                .thenReturn(false);
        when(requests.saveAndFlush(any(DeviceModelRequest.class)))
                .thenAnswer(invocation -> {
                    DeviceModelRequest request = invocation.getArgument(0);
                    ReflectionTestUtils.setField(request, "id", 501L);
                    return request;
                });
        when(categories.findByParentIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sourceModel));
        when(categories.saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category model = invocation.getArgument(0);
                    ReflectionTestUtils.setField(model, "id", 102L);
                    return model;
                });
        when(categories.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sourceModel));
        when(templates.findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(templateItems.findByChecklistTemplateIdOrderByDisplayOrderAsc(301L))
                .thenReturn(List.of(sourceItem));
        when(templates.saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> {
                    ChecklistTemplate target = invocation.getArgument(0);
                    ReflectionTestUtils.setField(target, "id", 302L);
                    return target;
                });

        var result = service.create(
                7L,
                new com.c203.limit.domain.product.dto.request.CreateDeviceModelRequest(
                        1L,
                        "Samsung",
                        "Galaxy S25",
                        "SM-S931N",
                        OsFamily.ANDROID));

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.resolvedModelId()).isEqualTo(102L);
        verify(catalogRegistrar).registerReported(any(Category.class), org.mockito.ArgumentMatchers.eq(7L));
        verify(templateItems).saveAllAndFlush(any());
    }

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
    void updateRelinksAndCorrectsLegacyCategoryModel() {
        Fixture fixture = fixture();
        Category oldParent = topLevel(1L, DeviceType.SMARTPHONE);
        Category correctedParent = topLevel(2L, DeviceType.SMARTPHONE);
        Category legacyModel = leaf(oldParent, 102L, "Galxy S25", "SM-S931", 1);
        Category sibling = leaf(correctedParent, 201L, "Galaxy S24", "SM-S921N", 1);
        DeviceModelRequest request = pendingRequest(501L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(2L)).thenReturn(Optional.of(correctedParent));
        when(fixture.categories()
                        .findFirstByParentIdAndManufacturerIgnoreCaseAndNameIgnoreCase(
                                1L, "Samsnug", "Galxy S25"))
                .thenReturn(Optional.of(legacyModel));
        when(fixture.categories().findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(2L))
                .thenReturn(List.of(sibling));
        ChecklistTemplate sourceTemplate = publishedTemplate(201L, 1, 301L);
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        201L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(fixture.templateItems().findByChecklistTemplateIdOrderByDisplayOrderAsc(301L))
                .thenReturn(List.of());
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        102L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(fixture.templates().saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceModelRequestResponse result =
                fixture.service().update(501L, 9L, updateRequest(2L));

        assertThat(result.resolvedModelId()).isEqualTo(102L);
        assertThat(legacyModel.getParent().getId()).isEqualTo(2L);
        assertThat(legacyModel.getName()).isEqualTo("Galaxy S25");
        verify(fixture.catalogRegistrar()).registerReported(legacyModel, 7L);
        verify(fixture.catalogRegistrar()).update(legacyModel);
    }

    @Test
    void reviewCompletesAlreadyProvisionedModel() {
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
        ReflectionTestUtils.setField(sourceModel, "id", 102L);
        DeviceModelRequest request =
                DeviceModelRequest.create(7L, 1L, "Samsung", "Galaxy S25", null, OsFamily.ANDROID);
        ReflectionTestUtils.setField(request, "id", 501L);
        request.provision(102L);

        when(requests.findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(categories.findById(102L)).thenReturn(Optional.of(sourceModel));

        var result = service.approve(501L, 9L, "공식 모델 확인");

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.resolvedCategoryId()).isEqualTo(102L);
        verify(catalogRegistrar).registerReported(sourceModel, 7L);
        verify(catalogRegistrar).completeReview(102L, 9L, "공식 모델 확인");
        verify(logs).save(any());
    }

    @Test
    void reviewRepairsLegacyRequestWithoutResolvedModel() {
        Fixture fixture = fixture();
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category sibling = leaf(parent, 101L, "Galaxy S24", "SM-S921N", 1);
        ChecklistTemplate sourceTemplate = publishedTemplate(101L, 1, 301L);
        DeviceModelRequest request = pendingRequest(501L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(1L)).thenReturn(Optional.of(parent));
        when(fixture.categories().findByParentIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sibling));
        when(fixture.categories().saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category model = invocation.getArgument(0);
                    ReflectionTestUtils.setField(model, "id", 102L);
                    return model;
                });
        when(fixture.categories().findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sibling));
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(fixture.templateItems().findByChecklistTemplateIdOrderByDisplayOrderAsc(301L))
                .thenReturn(List.of(templateItem(sourceTemplate)));
        when(fixture.templates().saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> {
                    ChecklistTemplate template = invocation.getArgument(0);
                    ReflectionTestUtils.setField(template, "id", 302L);
                    return template;
                });

        DeviceModelRequestResponse result =
                fixture.service().approve(501L, 9L, "레거시 요청 복구");

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.resolvedModelId()).isEqualTo(102L);
        verify(fixture.catalogRegistrar())
                .registerReported(any(Category.class), org.mockito.ArgumentMatchers.eq(7L));
        verify(fixture.catalogRegistrar()).completeReview(102L, 9L, "레거시 요청 복구");
        verify(fixture.actionLogs()).save(any());
    }

    @Test
    void reviewRelinksLegacyRequestToExistingCategoryModel() {
        Fixture fixture = fixture();
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category existingModel = leaf(parent, 102L, "Galxy S25", "SM-S931", 1);
        DeviceModelRequest request = pendingRequest(501L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(1L)).thenReturn(Optional.of(parent));
        when(fixture.categories()
                        .findFirstByParentIdAndManufacturerIgnoreCaseAndNameIgnoreCase(
                                1L, "Samsnug", "Galxy S25"))
                .thenReturn(Optional.of(existingModel));

        DeviceModelRequestResponse result =
                fixture.service().approve(501L, 9L, "기존 모델 연결");

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.resolvedModelId()).isEqualTo(102L);
        verify(fixture.catalogRegistrar()).registerReported(existingModel, 7L);
        verify(fixture.catalogRegistrar()).completeReview(102L, 9L, "기존 모델 연결");
        verify(fixture.templates(), never()).saveAndFlush(any(ChecklistTemplate.class));
    }

    @Test
    void rejectsCreateWhenCategoryIsNotAnActiveTopLevelCategory() {
        Fixture fixture = fixture();
        Category topLevel = topLevel(1L, DeviceType.SMARTPHONE);
        Category deactivated = topLevel(2L, DeviceType.SMARTPHONE);
        deactivated.deactivate();
        Category leaf = leaf(topLevel, 101L, "Galaxy S24", "SM-S921N", 1);
        when(fixture.categories().findById(9L)).thenReturn(Optional.empty());
        when(fixture.categories().findById(2L)).thenReturn(Optional.of(deactivated));
        when(fixture.categories().findById(101L)).thenReturn(Optional.of(leaf));

        for (long categoryId : new long[] {9L, 2L, 101L}) {
            assertThatThrownBy(() -> fixture.service().create(7L, createRequest(categoryId)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
        verify(fixture.requests(), never()).saveAndFlush(any());
    }

    @Test
    void rejectsCreateWhenDeviceTypeOrOsFamilyIsMissing() {
        Fixture fixture = fixture();
        Category withoutDeviceType = topLevel(3L, null);
        Category usable = topLevel(1L, DeviceType.SMARTPHONE);
        when(fixture.categories().findById(3L)).thenReturn(Optional.of(withoutDeviceType));
        when(fixture.categories().findById(1L)).thenReturn(Optional.of(usable));

        assertThatThrownBy(() -> fixture.service().create(7L, createRequest(3L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> fixture.service().create(
                        7L,
                        new CreateDeviceModelRequest(
                                1L, "Samsung", "Galaxy S25", "SM-S931N", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(fixture.requests(), never()).saveAndFlush(any());
    }

    @Test
    void rejectsCreateWhenTheSameModelIsAlreadyUnderReview() {
        Fixture fixture = fixture();
        when(fixture.categories().findById(1L))
                .thenReturn(Optional.of(topLevel(1L, DeviceType.SMARTPHONE)));
        when(fixture.requests()
                        .existsByParentCategoryIdAndManufacturerIgnoreCaseAndModelNameIgnoreCaseAndStatus(
                                1L, "Samsung", "Galaxy S25", DeviceModelRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> fixture.service().create(
                        7L,
                        new CreateDeviceModelRequest(
                                1L, "  Samsung  ", "  Galaxy S25  ", "SM-S931N",
                                OsFamily.ANDROID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_REQUEST_DUPLICATED));
        verify(fixture.requests(), never()).saveAndFlush(any());
        verifyNoInteractions(fixture.catalogRegistrar());
    }

    /** 모델 코드를 적지 않은 요청도 카탈로그 리프를 만들 수 있어야 한다. */
    @Test
    void generatesPlaceholderModelCodeAndNextDisplayOrderWhenRequestOmitsModelCode() {
        Fixture fixture = fixture();
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category sibling = leaf(parent, 101L, "Galaxy S24", "SM-S921N", 4);
        ChecklistTemplate sourceTemplate = publishedTemplate(101L, 1, 301L);
        ChecklistTemplateItem sourceItem = templateItem(sourceTemplate);
        when(fixture.categories().findById(1L)).thenReturn(Optional.of(parent));
        when(fixture.requests().saveAndFlush(any(DeviceModelRequest.class)))
                .thenAnswer(invocation -> {
                    DeviceModelRequest request = invocation.getArgument(0);
                    ReflectionTestUtils.setField(request, "id", 501L);
                    return request;
                });
        when(fixture.categories().findByParentIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sibling));
        when(fixture.categories().saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category model = invocation.getArgument(0);
                    ReflectionTestUtils.setField(model, "id", 102L);
                    return model;
                });
        when(fixture.categories().findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(sibling));
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(fixture.templateItems().findByChecklistTemplateIdOrderByDisplayOrderAsc(301L))
                .thenReturn(List.of(sourceItem));
        when(fixture.templates().saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> {
                    ChecklistTemplate target = invocation.getArgument(0);
                    ReflectionTestUtils.setField(target, "id", 302L);
                    return target;
                });

        var result = fixture.service().create(
                7L,
                new CreateDeviceModelRequest(
                        1L, "Samsung", "Galaxy S25", null, OsFamily.ANDROID));

        assertThat(result.modelCode()).isNull();
        ArgumentCaptor<Category> modelCaptor = ArgumentCaptor.forClass(Category.class);
        verify(fixture.categories()).saveAndFlush(modelCaptor.capture());
        assertThat(modelCaptor.getValue().getModelCode()).isEqualTo("REQUEST-501");
        assertThat(modelCaptor.getValue().getDisplayOrder()).isEqualTo(5);
        ArgumentCaptor<ChecklistTemplate> templateCaptor =
                ArgumentCaptor.forClass(ChecklistTemplate.class);
        verify(fixture.templates()).saveAndFlush(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getVersion()).isEqualTo(1);
        assertThat(templateCaptor.getValue().getStatus())
                .isEqualTo(ChecklistTemplateStatus.PUBLISHED);
    }

    /** 형제 모델에 게시된 템플릿이 하나도 없으면 복제할 원본이 없다. */
    @Test
    void rejectsCreateWhenNoSiblingModelHasAPublishedTemplate() {
        Fixture fixture = fixture();
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category provisionedItself = leaf(parent, 102L, "Galaxy S25", "SM-S931N", 2);
        Category sibling = leaf(parent, 101L, "Galaxy S24", "SM-S921N", 1);
        when(fixture.categories().findById(1L)).thenReturn(Optional.of(parent));
        when(fixture.requests().saveAndFlush(any(DeviceModelRequest.class)))
                .thenAnswer(invocation -> {
                    DeviceModelRequest request = invocation.getArgument(0);
                    ReflectionTestUtils.setField(request, "id", 501L);
                    return request;
                });
        when(fixture.categories().findByParentIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of());
        when(fixture.categories().saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category model = invocation.getArgument(0);
                    ReflectionTestUtils.setField(model, "id", 102L);
                    return model;
                });
        when(fixture.categories().findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(provisionedItself, sibling));
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        101L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service().create(7L, createRequest(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_TEMPLATE_NOT_FOUND));
        verify(fixture.templates(), never()).saveAndFlush(any(ChecklistTemplate.class));
    }

    @Test
    void listsPendingRequestsByDefaultAndFiltersByRequestedStatus() {
        Fixture fixture = fixture();
        DeviceModelRequest pending = pendingRequest(501L);
        DeviceModelRequest approved = pendingRequest(502L);
        approved.provision(102L);
        approved.approve(9L, 102L, "확인");
        when(fixture.requests().findByStatusOrderByCreatedAtAsc(DeviceModelRequestStatus.PENDING))
                .thenReturn(List.of(pending));
        when(fixture.requests().findByStatusOrderByCreatedAtAsc(DeviceModelRequestStatus.APPROVED))
                .thenReturn(List.of(approved));
        when(fixture.requests().findByStatusOrderByCreatedAtAsc(DeviceModelRequestStatus.REJECTED))
                .thenReturn(List.of());

        assertThat(fixture.service().list(null))
                .extracting(DeviceModelRequestResponse::requestId)
                .containsExactly(501L);
        assertThat(fixture.service().list(DeviceModelRequestStatus.APPROVED))
                .extracting(DeviceModelRequestResponse::status)
                .containsExactly("APPROVED");
        assertThat(fixture.service().list(DeviceModelRequestStatus.REJECTED)).isEmpty();
    }

    @Test
    void rejectsUpdateForAnUnknownRequest() {
        Fixture fixture = fixture();
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service().update(501L, 9L, updateRequest(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_REQUEST_NOT_FOUND));
        verifyNoInteractions(fixture.categories());
    }

    @Test
    void rejectsUpdateWhenRequestWasAlreadyReviewed() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        request.approve(9L, 102L, "확인");
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> fixture.service().update(501L, 9L, updateRequest(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_REQUEST_STATE_CONFLICT));
        verifyNoInteractions(fixture.actionLogs());
    }

    @Test
    void rejectsUpdateWhenAnotherPendingRequestUsesTheSameModel() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(1L))
                .thenReturn(Optional.of(topLevel(1L, DeviceType.SMARTPHONE)));
        when(fixture.requests()
                        .existsByParentCategoryIdAndManufacturerIgnoreCaseAndModelNameIgnoreCaseAndStatusAndIdNot(
                                1L,
                                "Samsung",
                                "Galaxy S25",
                                DeviceModelRequestStatus.PENDING,
                                501L))
                .thenReturn(true);

        assertThatThrownBy(() -> fixture.service().update(501L, 9L, updateRequest(1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_REQUEST_DUPLICATED));
        verifyNoInteractions(fixture.actionLogs());
    }

    /** 이미 만들어 둔 리프를 다른 상위 카테고리로 옮기면 그 카테고리의 기준 템플릿을 새로 붙여야 한다. */
    @Test
    void movesProvisionedModelAndRepublishesBaseTemplateWhenCategoryChanges() {
        Fixture fixture = fixture();
        Category oldParent = topLevel(1L, DeviceType.SMARTPHONE);
        Category newParent = topLevel(2L, DeviceType.LAPTOP);
        Category model = leaf(oldParent, 102L, "Galxy S25", "SM-S931", 1);
        Category sibling = leaf(newParent, 201L, "Galaxy Book4", "NT960", 1);
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        ChecklistTemplate sourceTemplate = publishedTemplate(201L, 2, 401L);
        ChecklistTemplate latestPublished = publishedTemplate(102L, 3, 402L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(2L)).thenReturn(Optional.of(newParent));
        when(fixture.categories().findById(102L)).thenReturn(Optional.of(model));
        when(fixture.categories().findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(2L))
                .thenReturn(List.of(sibling));
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        201L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(fixture.templateItems().findByChecklistTemplateIdOrderByDisplayOrderAsc(401L))
                .thenReturn(List.of(templateItem(sourceTemplate)));
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        102L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(latestPublished));
        when(fixture.templates().saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> {
                    ChecklistTemplate target = invocation.getArgument(0);
                    ReflectionTestUtils.setField(target, "id", 403L);
                    return target;
                });

        var result = fixture.service().update(
                501L,
                9L,
                new UpdateDeviceModelRequest(
                        2L, "  Samsung  ", "  Galaxy S25  ", "SM-S931N", OsFamily.ANDROID));

        assertThat(result.categoryId()).isEqualTo(2L);
        assertThat(model.getParent().getId()).isEqualTo(2L);
        assertThat(model.getName()).isEqualTo("Galaxy S25");
        assertThat(model.getManufacturer()).isEqualTo("Samsung");
        verify(fixture.catalogRegistrar()).update(model);
        ArgumentCaptor<ChecklistTemplate> captor =
                ArgumentCaptor.forClass(ChecklistTemplate.class);
        verify(fixture.templates()).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(4);
        verify(fixture.templateItems()).saveAllAndFlush(any());
        verify(fixture.actionLogs()).save(any());
    }

    @Test
    void startsBaseTemplateAtVersionOneWhenMovedModelHasNoPublishedTemplate() {
        Fixture fixture = fixture();
        Category oldParent = topLevel(1L, DeviceType.SMARTPHONE);
        Category newParent = topLevel(2L, DeviceType.LAPTOP);
        Category model = leaf(oldParent, 102L, "Galxy S25", "SM-S931", 1);
        // 상위 카테고리가 비어 있는 리프도 이동 대상이 될 수 있다.
        ReflectionTestUtils.setField(model, "parent", null);
        Category sibling = leaf(newParent, 201L, "Galaxy Book4", "NT960", 1);
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        ChecklistTemplate sourceTemplate = publishedTemplate(201L, 2, 401L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(2L)).thenReturn(Optional.of(newParent));
        when(fixture.categories().findById(102L)).thenReturn(Optional.of(model));
        when(fixture.categories().findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(2L))
                .thenReturn(List.of(sibling));
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionAsc(
                        201L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(sourceTemplate));
        when(fixture.templateItems().findByChecklistTemplateIdOrderByDisplayOrderAsc(401L))
                .thenReturn(List.of());
        when(fixture.templates().findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        102L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(fixture.templates().saveAndFlush(any(ChecklistTemplate.class)))
                .thenAnswer(invocation -> {
                    ChecklistTemplate target = invocation.getArgument(0);
                    ReflectionTestUtils.setField(target, "id", 403L);
                    return target;
                });

        fixture.service().update(501L, 9L, updateRequest(2L));

        ArgumentCaptor<ChecklistTemplate> captor =
                ArgumentCaptor.forClass(ChecklistTemplate.class);
        verify(fixture.templates()).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
    }

    @Test
    void keepsBaseTemplateWhenProvisionedModelStaysInTheSameCategory() {
        Fixture fixture = fixture();
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category model = leaf(parent, 102L, "Galxy S25", "SM-S931", 1);
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(1L)).thenReturn(Optional.of(parent));
        when(fixture.categories().findById(102L)).thenReturn(Optional.of(model));

        fixture.service().update(501L, 9L, updateRequest(1L));

        verify(fixture.catalogRegistrar()).update(model);
        verify(fixture.templates(), never()).saveAndFlush(any(ChecklistTemplate.class));
        verify(fixture.templateItems(), never()).saveAllAndFlush(any());
        verify(fixture.actionLogs()).save(any());
    }

    @Test
    void updatesRequestDetailsWhenProvisionedModelRowIsMissing() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(1L))
                .thenReturn(Optional.of(topLevel(1L, DeviceType.SMARTPHONE)));
        when(fixture.categories().findById(102L)).thenReturn(Optional.empty());

        DeviceModelRequestResponse result =
                fixture.service().update(501L, 9L, updateRequest(1L));

        assertThat(result.manufacturer()).isEqualTo("Samsung");
        assertThat(result.modelName()).isEqualTo("Galaxy S25");
        assertThat(result.resolvedModelId()).isEqualTo(102L);
        verify(fixture.actionLogs()).save(any());
        verifyNoInteractions(fixture.catalogRegistrar());
    }

    @Test
    void rejectsApprovalWhenModelWasNeverProvisioned() {
        Fixture fixture = fixture();
        when(fixture.requests().findByIdForUpdate(501L))
                .thenReturn(Optional.of(pendingRequest(501L)));

        assertThatThrownBy(() -> fixture.service().approve(501L, 9L, "확인"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_NOT_FOUND));
        verifyNoInteractions(fixture.catalogRegistrar(), fixture.actionLogs());
    }

    @Test
    void rejectsApprovalForAnUnknownRequest() {
        Fixture fixture = fixture();
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service().approve(501L, 9L, "확인"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_REQUEST_NOT_FOUND));
    }

    @Test
    void rejectionDeactivatesTheProvisionedModelInBothCatalogs() {
        Fixture fixture = fixture();
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category model = leaf(parent, 102L, "Galaxy S25", "SM-S931N", 1);
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(102L)).thenReturn(Optional.of(model));

        var result = fixture.service().reject(501L, 9L, "중복 모델");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reviewNote()).isEqualTo("중복 모델");
        assertThat(model.isActive()).isFalse();
        verify(fixture.catalogRegistrar()).deactivate(102L);
        verify(fixture.actionLogs()).save(any());
    }

    @Test
    void deleteClosesRequestAndDeactivatesProvisionedModel() {
        Fixture fixture = fixture();
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category model = leaf(parent, 102L, "Galaxy S25", "SM-S931N", 1);
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(102L)).thenReturn(Optional.of(model));

        fixture.service().delete(501L, 9L);

        assertThat(request.getStatus()).isEqualTo(DeviceModelRequestStatus.REJECTED);
        assertThat(request.getReviewNote()).isEqualTo("관리자 삭제");
        assertThat(model.isActive()).isFalse();
        verify(fixture.catalogRegistrar()).deactivate(102L);
        verify(fixture.actionLogs()).save(any());
    }

    @Test
    void deleteAlsoClosesLegacyRequestWithoutResolvedModel() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));

        fixture.service().delete(501L, 9L);

        assertThat(request.getStatus()).isEqualTo(DeviceModelRequestStatus.REJECTED);
        verify(fixture.categories())
                .findFirstByParentIdAndManufacturerIgnoreCaseAndNameIgnoreCase(
                        1L, "Samsnug", "Galxy S25");
        verifyNoInteractions(fixture.catalogRegistrar());
        verify(fixture.actionLogs()).save(any());
    }

    /** 리프 행이 이미 사라졌어도 신규 카탈로그 쪽 비활성화는 그대로 진행돼야 한다. */
    @Test
    void rejectionStillDeactivatesCatalogWhenLeafCategoryRowIsGone() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        request.provision(102L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(102L)).thenReturn(Optional.empty());

        fixture.service().reject(501L, 9L, null);

        verify(fixture.catalogRegistrar()).deactivate(102L);
    }

    @Test
    void rejectionSkipsCatalogCleanupWhenNothingWasProvisioned() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));

        var result = fixture.service().reject(501L, 9L, "정보 부족");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.resolvedModelId()).isNull();
        verify(fixture.categories())
                .findFirstByParentIdAndManufacturerIgnoreCaseAndNameIgnoreCase(
                        1L, "Samsnug", "Galxy S25");
        verifyNoInteractions(fixture.catalogRegistrar());
        verify(fixture.actionLogs()).save(any());
    }

    @Test
    void rejectsRejectionWhenRequestWasAlreadyRejected() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        request.reject(9L, "정보 부족");
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> fixture.service().reject(501L, 9L, "재거절"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEVICE_MODEL_REQUEST_STATE_CONFLICT));
        verifyNoInteractions(fixture.actionLogs());
    }

    @Test
    void rejectsUpdateWhenTargetCategoryIsNotAnActiveTopLevelCategory() {
        Fixture fixture = fixture();
        DeviceModelRequest request = pendingRequest(501L);
        Category parent = topLevel(1L, DeviceType.SMARTPHONE);
        Category leafTarget = leaf(parent, 101L, "Galaxy S24", "SM-S921N", 1);
        Category deactivated = topLevel(2L, DeviceType.SMARTPHONE);
        deactivated.deactivate();
        when(fixture.requests().findByIdForUpdate(501L)).thenReturn(Optional.of(request));
        when(fixture.categories().findById(101L)).thenReturn(Optional.of(leafTarget));
        when(fixture.categories().findById(2L)).thenReturn(Optional.of(deactivated));

        for (long categoryId : new long[] {101L, 2L}) {
            assertThatThrownBy(
                            () -> fixture.service().update(501L, 9L, updateRequest(categoryId)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
        verifyNoInteractions(fixture.actionLogs(), fixture.catalogRegistrar());
    }

    private record Fixture(
            CategoryRepository categories,
            DeviceModelRequestRepository requests,
            ChecklistTemplateRepository templates,
            ChecklistTemplateItemRepository templateItems,
            AdminActionLogRepository actionLogs,
            DeviceCatalogRegistrar catalogRegistrar,
            DeviceModelRequestService service) {}

    private Fixture fixture() {
        CategoryRepository categories = mock(CategoryRepository.class);
        DeviceModelRequestRepository requests = mock(DeviceModelRequestRepository.class);
        ChecklistTemplateRepository templates = mock(ChecklistTemplateRepository.class);
        ChecklistTemplateItemRepository templateItems =
                mock(ChecklistTemplateItemRepository.class);
        AdminActionLogRepository actionLogs = mock(AdminActionLogRepository.class);
        DeviceCatalogRegistrar catalogRegistrar = mock(DeviceCatalogRegistrar.class);
        return new Fixture(
                categories,
                requests,
                templates,
                templateItems,
                actionLogs,
                catalogRegistrar,
                new DeviceModelRequestService(
                        categories,
                        requests,
                        templates,
                        templateItems,
                        actionLogs,
                        catalogRegistrar));
    }

    private CreateDeviceModelRequest createRequest(Long categoryId) {
        return new CreateDeviceModelRequest(
                categoryId, "Samsung", "Galaxy S25", "SM-S931N", OsFamily.ANDROID);
    }

    private UpdateDeviceModelRequest updateRequest(Long categoryId) {
        return new UpdateDeviceModelRequest(
                categoryId, "Samsung", "Galaxy S25", "SM-S931N", OsFamily.ANDROID);
    }

    private DeviceModelRequest pendingRequest(Long id) {
        DeviceModelRequest request = DeviceModelRequest.create(
                7L, 1L, "Samsnug", "Galxy S25", "SM-S931", OsFamily.ANDROID);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private Category topLevel(Long id, DeviceType deviceType) {
        Category category = Category.createTopLevel("카테고리-" + id, deviceType, 1);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private Category leaf(
            Category parent, Long id, String name, String modelCode, int displayOrder) {
        Category leaf = Category.createLeaf(
                parent,
                name,
                parent.getDeviceType(),
                "Samsung",
                OsFamily.ANDROID,
                modelCode,
                List.of(),
                displayOrder);
        ReflectionTestUtils.setField(leaf, "id", id);
        return leaf;
    }

    private ChecklistTemplate publishedTemplate(Long categoryId, int version, Long id) {
        ChecklistTemplate template = ChecklistTemplate.createDraft(categoryId, version);
        ReflectionTestUtils.setField(template, "id", id);
        template.publish();
        return template;
    }

    private ChecklistTemplateItem templateItem(ChecklistTemplate template) {
        return ChecklistTemplateItem.create(
                template,
                "EXT-001",
                "외관",
                "외관 확인",
                "기기를 촬영하세요.",
                EvidenceType.PHOTO,
                AutomationType.NONE,
                true,
                1);
    }
}
