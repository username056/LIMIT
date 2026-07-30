package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.repository.AccountRemovalGuideRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.product.dto.request.CreateDeviceModelRequest;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTests {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ChecklistTemplateRepository templateRepository;
    @Mock private ChecklistTemplateItemRepository templateItemRepository;
    @Mock private AccountRemovalGuideRepository guideRepository;

    private ProductCatalogService service;

    @BeforeEach
    void setUp() {
        service =
                new ProductCatalogService(
                        categoryRepository,
                        templateRepository,
                        templateItemRepository,
                        guideRepository);
    }

    @Test
    void createsCustomLaptopModel() {
        Category parent = topLevel(10L, "노트북", DeviceType.LAPTOP);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(categoryRepository
                        .findFirstByParentIdAndManufacturerIgnoreCaseAndNameIgnoreCaseAndIsActiveTrue(
                                10L, "LG", "gram 16"))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByParentIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of());
        when(categoryRepository.saveAndFlush(any(Category.class)))
                .thenAnswer(
                        invocation -> {
                            Category model = invocation.getArgument(0);
                            ReflectionTestUtils.setField(model, "id", 202L);
                            return model;
                        });

        var response =
                service.createModel(
                        new CreateDeviceModelRequest(
                                10L, " LG ", " gram 16 ", "", OsFamily.WINDOWS));

        assertThat(response.getDeviceModelId()).isEqualTo(202L);
        assertThat(response.getManufacturerName()).isEqualTo("LG");
        assertThat(response.getModelName()).isEqualTo("gram 16");
        assertThat(response.getModelCode()).startsWith("CUSTOM-");
        assertThat(response.getDefaultOs()).isEqualTo("WINDOWS");
    }

    @Test
    void reusesExistingModelWithSameManufacturerAndName() {
        Category parent = topLevel(10L, "노트북", DeviceType.LAPTOP);
        Category existing =
                Category.createLeaf(
                        parent,
                        "gram 16",
                        DeviceType.LAPTOP,
                        "LG",
                        OsFamily.WINDOWS,
                        "16Z90S",
                        List.of(),
                        1);
        ReflectionTestUtils.setField(existing, "id", 202L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(categoryRepository
                        .findFirstByParentIdAndManufacturerIgnoreCaseAndNameIgnoreCaseAndIsActiveTrue(
                                10L, "LG", "gram 16"))
                .thenReturn(Optional.of(existing));

        var response =
                service.createModel(
                        new CreateDeviceModelRequest(
                                10L, "LG", "gram 16", "16Z90S", OsFamily.WINDOWS));

        assertThat(response.getDeviceModelId()).isEqualTo(202L);
        verify(categoryRepository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void rejectsCustomPhoneModelUntilInspectionTemplateIsAvailable() {
        Category parent = topLevel(20L, "스마트폰", DeviceType.SMARTPHONE);
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(
                        () ->
                                service.createModel(
                                        new CreateDeviceModelRequest(
                                                20L,
                                                "Samsung",
                                                "Galaxy S25",
                                                "SM-S931N",
                                                OsFamily.ANDROID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHECKLIST_DEVICE_TYPE_NOT_SUPPORTED);
    }

    private Category topLevel(Long id, String name, DeviceType deviceType) {
        Category category = Category.createTopLevel(name, deviceType, 1);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
