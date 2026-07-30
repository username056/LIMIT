package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChecklistGenerationServiceTests {
    private CategoryRepository categoryRepository;
    private ChecklistSupplementClient supplementClient;
    private ChecklistGenerationService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        supplementClient = mock(ChecklistSupplementClient.class);
        service = new ChecklistGenerationService(
                categoryRepository, new LaptopChecklistPolicy(), supplementClient);
    }

    @Test
    void returnsOfficialAiSuggestionsAndDecoratesConfirmedFeature() {
        Category model = laptop(OsFamily.WINDOWS);
        when(categoryRepository.findById(201L)).thenReturn(Optional.of(model));
        when(supplementClient.suggest(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ChecklistSupplementResult(
                        true,
                        List.of(new ChecklistSuggestion(
                                LaptopFeatureCode.CAMERA,
                                ChecklistEvidenceStatus.VERIFIED,
                                "official specification",
                                "카메라 앱에서 영상을 확인하세요.",
                                "https://www.samsung.com/sec/support/model/NT960/",
                                "Samsung support")),
                        List.of("RJ45_PORT", "MICROSD_SLOT", "조도 센서")));

        GeneratedChecklist result =
                service.generateForModel(201L, Set.of(LaptopFeatureCode.CAMERA));

        assertThat(result.aiApplied()).isTrue();
        assertThat(result.items()).hasSize(14);
        assertThat(result.aiSuggestions()).hasSize(1);
        assertThat(result.reviewCandidates()).containsExactly("조도 센서");
        assertThat(result.items())
                .filteredOn(item -> item.featureCode() == LaptopFeatureCode.CAMERA)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.evidenceStatus())
                            .isEqualTo(ChecklistEvidenceStatus.VERIFIED);
                    assertThat(item.sourceUrl()).startsWith("https://www.samsung.com/");
                });
    }

    @Test
    void fallsBackToVerifiedBaseWhenAiFails() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));
        when(supplementClient.suggest(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("temporary failure"));

        GeneratedChecklist result = service.generateForModel(201L, Set.of());

        assertThat(result.aiApplied()).isFalse();
        assertThat(result.items()).hasSize(13);
        assertThat(result.aiSuggestions()).isEmpty();
    }

    @Test
    void createsProductSnapshotWithoutCallingAiAgain() {
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop(OsFamily.WINDOWS)));

        GeneratedChecklist result = service.generateSnapshotIfLaptop(
                        201L, Set.of(LaptopFeatureCode.CAMERA))
                .orElseThrow();

        assertThat(result.aiApplied()).isFalse();
        assertThat(result.items()).hasSize(14);
        assertThat(result.items())
                .extracting(GeneratedChecklistItem::featureCode)
                .contains(LaptopFeatureCode.CAMERA);
        assertThat(result.aiSuggestions()).isEmpty();
        assertThat(result.reviewCandidates()).isEmpty();
        verifyNoInteractions(supplementClient);
    }

    @Test
    void supportsGenericLinuxChecklistForDirectInput() {
        when(supplementClient.suggest(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ChecklistSupplementResult.unavailable());

        GeneratedChecklist result = service.generateCustom(
                "Unknown", "Developer Laptop", null, OsFamily.LINUX, Set.of());

        assertThat(result.deviceModelId()).isNull();
        assertThat(result.osFamily()).isEqualTo(OsFamily.LINUX);
        assertThat(result.items()).hasSize(12);
    }

    @Test
    void rejectsNonLaptopCatalogModel() {
        Category phone =
                Category.createTopLevel("Smartphone", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(phone, "id", 101L);
        when(categoryRepository.findById(101L)).thenReturn(Optional.of(phone));

        assertThatThrownBy(() -> service.generateForModel(101L, Set.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CHECKLIST_DEVICE_TYPE_NOT_SUPPORTED));
    }

    private Category laptop(OsFamily osFamily) {
        Category parent = Category.createTopLevel("Laptop", DeviceType.LAPTOP, 1);
        ReflectionTestUtils.setField(parent, "id", 2L);
        Category model = Category.createLeaf(
                parent,
                "Galaxy Book4 Pro",
                DeviceType.LAPTOP,
                "Samsung",
                osFamily,
                "NT960",
                List.of(512, 1024),
                1);
        ReflectionTestUtils.setField(model, "id", 201L);
        return model;
    }
}
