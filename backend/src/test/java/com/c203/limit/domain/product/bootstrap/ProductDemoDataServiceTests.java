package com.c203.limit.domain.product.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.ChecklistTemplate;
import com.c203.limit.domain.inspection.enums.ChecklistTemplateStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.dto.response.ProductCreatedResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.service.ProductApplicationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductDemoDataServiceTests {
    @Mock CategoryRepository categoryRepository;
    @Mock ChecklistTemplateRepository templateRepository;
    @Mock ListingRepository listingRepository;
    @Mock ProductApplicationService productApplicationService;

    @Test
    void createsProductsWithMixedStatusesAndSkipsExistingTitles() {
        Category parent = mock(Category.class);
        Category model = mock(Category.class);
        when(parent.getId()).thenReturn(10L);
        when(model.getId()).thenReturn(20L);
        when(model.getParent()).thenReturn(parent);
        when(model.getManufacturer()).thenReturn("Samsung");
        when(model.getName()).thenReturn("Galaxy S24");
        when(categoryRepository.findModels(
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(model)));
        when(templateRepository.findFirstByCategoryIdAndStatusOrderByVersionDesc(
                        20L, ChecklistTemplateStatus.PUBLISHED))
                .thenReturn(Optional.of(mock(ChecklistTemplate.class)));
        when(listingRepository.existsBySellerIdAndTitleAndDeletedAtIsNull(
                        101L, "[데모 01-01] Samsung Galaxy S24"))
                .thenReturn(true);
        when(productApplicationService.create(
                        org.mockito.ArgumentMatchers.eq(101L), any(CreateProductRequest.class)))
                .thenReturn(
                        new ProductCreatedResponse(
                                2002L, "DRAFT", 20L, 1, 1, 0, OffsetDateTime.now()),
                        new ProductCreatedResponse(
                                2003L, "DRAFT", 20L, 1, 1, 0, OffsetDateTime.now()),
                        new ProductCreatedResponse(
                                2004L, "DRAFT", 20L, 1, 1, 0, OffsetDateTime.now()));

        int created = service().seedForSeller(101L, 1, 4);

        assertThat(created).isEqualTo(3);
        var statusRequest = ArgumentCaptor.forClass(TransitionProductStatusRequest.class);
        verify(productApplicationService, org.mockito.Mockito.times(3))
                .transition(org.mockito.ArgumentMatchers.eq(101L), any(), statusRequest.capture());
        assertThat(statusRequest.getAllValues())
                .extracting(TransitionProductStatusRequest::getTargetStatus)
                .containsExactly("ON_SALE", "ON_SALE", "HIDDEN");
    }

    @Test
    void skipsProductsWhenNoPublishedModelExists() {
        when(categoryRepository.findModels(
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service().seedForSeller(101L, 1, 4)).isZero();
        verify(productApplicationService, never())
                .create(any(), any(CreateProductRequest.class));
    }

    private ProductDemoDataService service() {
        return new ProductDemoDataService(
                categoryRepository,
                templateRepository,
                listingRepository,
                productApplicationService);
    }
}
