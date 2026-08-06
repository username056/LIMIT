package com.c203.limit.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelDetailResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelOptionsResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelSummaryResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideResponse;
import com.c203.limit.domain.product.entity.DeviceVariantAxis;
import com.c203.limit.domain.product.service.DeviceCatalogOptionService;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.domain.product.service.ProductCatalogService.ModelPage;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageMetaResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductCatalogControllerTests {
    private static final Long MODEL_ID = 101L;

    @Mock ProductCatalogService catalogService;
    @Mock DeviceCatalogOptionService optionService;
    ProductCatalogController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductCatalogController(catalogService, optionService);
    }

    @Test
    void returnsVariantOptionsForRequestedModel() {
        DeviceModelOptionsResponse response = mock(DeviceModelOptionsResponse.class);
        when(optionService.options(eq(MODEL_ID), anyMap())).thenReturn(response);

        var result =
                controller.getDeviceModelOptions(
                        MODEL_ID, "Black", null, "16", "   ", "", "RTX 4050", "WIFI6");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).isSameAs(response);
    }

    @Test
    void passesSelectedVariantAxesToOptionService() {
        DeviceModelOptionsResponse response = mock(DeviceModelOptionsResponse.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<DeviceVariantAxis, String>> selection =
                ArgumentCaptor.forClass(Map.class);
        when(optionService.options(eq(MODEL_ID), selection.capture())).thenReturn(response);

        controller.getDeviceModelOptions(
                MODEL_ID, "Black", "256", null, "14", null, null, "   ");

        assertThat(selection.getValue())
                .containsOnly(
                        Map.entry(DeviceVariantAxis.COLOR, "Black"),
                        Map.entry(DeviceVariantAxis.STORAGE_GB, "256"),
                        Map.entry(DeviceVariantAxis.SCREEN_SIZE_INCHES, "14"));
    }

    @Test
    void passesEmptySelectionWhenEveryVariantFilterIsBlank() {
        DeviceModelOptionsResponse response = mock(DeviceModelOptionsResponse.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<DeviceVariantAxis, String>> selection =
                ArgumentCaptor.forClass(Map.class);
        when(optionService.options(eq(MODEL_ID), selection.capture())).thenReturn(response);

        controller.getDeviceModelOptions(MODEL_ID, null, "", "  ", null, null, null, null);

        assertThat(selection.getValue()).isEmpty();
    }

    @Test
    void returnsDeviceCategoriesForRequestedParent() {
        DeviceCategoryResponse category = mock(DeviceCategoryResponse.class);
        when(catalogService.categories(5L, true)).thenReturn(List.of(category));

        var result = controller.getDeviceCategories(5L, true);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().data()).containsExactly(category);
        assertThat(result.getBody().meta()).isNull();
    }

    @Test
    void returnsDeviceModelsWithPagingMeta() {
        DeviceModelSummaryResponse summary = mock(DeviceModelSummaryResponse.class);
        when(catalogService.models(5L, 7L, "galaxy", 1, 20))
                .thenReturn(new ModelPage(List.of(summary), 1, 20, 25L, 2, false));

        var result = controller.getDeviceModels(5L, 7L, "galaxy", 1, 20);

        assertThat(result.getBody().data()).containsExactly(summary);
        assertThat(result.getBody().meta())
                .isEqualTo(new PageMetaResponse(1, 20, 25L, 2, false));
    }

    @Test
    void propagatesInvalidPagingRejectionFromCatalogService() {
        when(catalogService.models(null, null, null, -1, 20))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        assertThatThrownBy(() -> controller.getDeviceModels(null, null, null, -1, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void returnsDeviceModelDetail() {
        DeviceModelDetailResponse detail = mock(DeviceModelDetailResponse.class);
        when(catalogService.model(MODEL_ID)).thenReturn(detail);

        var result = controller.getDeviceModel(MODEL_ID);

        assertThat(result.getBody().data()).isSameAs(detail);
    }

    @Test
    void returnsChecklistTemplateForModel() {
        ChecklistTemplateResponse template = mock(ChecklistTemplateResponse.class);
        when(catalogService.checklistTemplate(MODEL_ID)).thenReturn(template);

        var result = controller.getChecklistTemplate(MODEL_ID);

        assertThat(result.getBody().data()).isSameAs(template);
    }

    @Test
    void returnsHandoverGuideForModel() {
        HandoverGuideResponse guide = mock(HandoverGuideResponse.class);
        when(catalogService.handoverGuide(MODEL_ID)).thenReturn(guide);

        var result = controller.getHandoverGuide(MODEL_ID);

        assertThat(result.getBody().data()).isSameAs(guide);
    }
}
