package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelStatusRequest;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.service.ModelChecklistResearchService;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.domain.product.repository.DeviceModelRepository;
import com.c203.limit.domain.product.repository.DeviceModelRequestRepository;
import com.c203.limit.domain.product.repository.DeviceVariantRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceModelManagementServiceTests {

    @Mock private DeviceModelRepository modelRepository;
    @Mock private DeviceModelRequestRepository requestRepository;
    @Mock private DeviceModelRequestService requestService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChecklistTemplateRepository templateRepository;
    @Mock private ChecklistTemplateItemRepository templateItemRepository;
    @Mock private DeviceCatalogRegistrar catalogRegistrar;
    @Mock private ModelChecklistResearchService researchService;
    @Mock private AdminActionLogRepository actionLogRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private DeviceVariantRepository variantRepository;
    @Mock private ListingImageRepository imageRepository;
    @Mock private ListingChecklistItemRepository listingChecklistItemRepository;
    @Mock private EvidenceRepository evidenceRepository;
    @Mock private MediaUrlResolver mediaUrlResolver;

    private DeviceModelManagementService service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new DeviceModelManagementService(
                modelRepository,
                requestRepository,
                requestService,
                categoryRepository,
                templateRepository,
                templateItemRepository,
                catalogRegistrar,
                researchService,
                actionLogRepository,
                listingRepository,
                variantRepository,
                imageRepository,
                listingChecklistItemRepository,
                evidenceRepository,
                mediaUrlResolver));
    }

    @Test
    void deactivationPreservesExistingListingsAndStoresAuditReason() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        Category legacyModel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(legacyModel));
        when(legacyModel.getParent()).thenReturn(org.mockito.Mockito.mock(Category.class));
        doReturn(null).when(service).detail(202L);

        service.updateStatus(
                202L,
                9L,
                new UpdateDeviceModelStatusRequest(false, "  중복 등록 모델  ", null));

        verify(legacyModel).deactivate();
        verify(model).deactivate(9L, "중복 등록 모델", null);
        verify(actionLogRepository).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(listingRepository);
    }

    @Test
    void deactivationRejectsTheSameModelAsReplacement() {
        DeviceModel model = org.mockito.Mockito.mock(DeviceModel.class);
        Category legacyModel = org.mockito.Mockito.mock(Category.class);
        when(modelRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(202L)).thenReturn(Optional.of(legacyModel));
        when(legacyModel.getParent()).thenReturn(org.mockito.Mockito.mock(Category.class));

        assertThatThrownBy(() -> service.updateStatus(
                        202L,
                        9L,
                        new UpdateDeviceModelStatusRequest(false, "중복", 202L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(model, never()).deactivate(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(listingRepository);
    }
}
