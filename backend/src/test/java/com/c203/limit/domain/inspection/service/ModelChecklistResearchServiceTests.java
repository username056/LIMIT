package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.response.ChecklistResearchResponse;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.inspection.checklist.ChecklistSupplementClient;
import com.c203.limit.domain.inspection.checklist.ChecklistSupplementResult;
import com.c203.limit.domain.inspection.checklist.DeviceChecklistFeatureCatalog;
import com.c203.limit.domain.inspection.checklist.LaptopChecklistPolicy;
import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateItemRepository;
import com.c203.limit.domain.inspection.repository.ChecklistTemplateRepository;
import com.c203.limit.domain.inspection.repository.ModelChecklistResearchRepository;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ModelChecklistResearchServiceTests {

    private ModelChecklistResearchRepository researchRepository;
    private CategoryRepository categoryRepository;
    private AdminActionLogRepository actionLogRepository;
    private ChecklistSupplementClient supplementClient;
    private ModelChecklistResearchService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        researchRepository = mock(ModelChecklistResearchRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        actionLogRepository = mock(AdminActionLogRepository.class);
        supplementClient = mock(ChecklistSupplementClient.class);
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        when(researchRepository.saveAndFlush(any(ModelChecklistResearch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        objectMapper = new ObjectMapper();
        service = new ModelChecklistResearchService(
                researchRepository,
                categoryRepository,
                mock(ChecklistTemplateRepository.class),
                mock(ChecklistTemplateItemRepository.class),
                actionLogRepository,
                new LaptopChecklistPolicy(),
                new DeviceChecklistFeatureCatalog(),
                supplementClient,
                objectMapper,
                transactionManager);
    }

    @Test
    void retriesFailedResearchAndMovesItToPendingReview() throws Exception {
        ModelChecklistResearch research = failedResearch();
        when(researchRepository.findByIdForUpdate(501L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(new ChecklistSupplementResult(true, List.of(), List.of()));

        ChecklistResearchResponse response = service.retry(501L, 900L);

        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        assertThat(response.failureCode()).isNull();
        assertThat(response.failureMessage()).isNull();
        assertThat(research.getStatus())
                .isEqualTo(ModelChecklistResearchStatus.PENDING_REVIEW);
        verify(actionLogRepository).save(any());
    }

    @Test
    void keepsFailureReasonWhenRetryFailsAgain() throws Exception {
        ModelChecklistResearch research = failedResearch();
        when(researchRepository.findByIdForUpdate(501L))
                .thenReturn(Optional.of(research));
        when(categoryRepository.findById(201L))
                .thenReturn(Optional.of(laptop()));
        when(supplementClient.suggest(any()))
                .thenReturn(ChecklistSupplementResult.requestFailed());

        ChecklistResearchResponse response = service.retry(501L, 900L);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureCode()).isEqualTo("AI_REQUEST_FAILED");
        assertThat(response.failureMessage()).contains("시간 초과");
        assertThat(research.getResultJson())
                .contains("\"failureCode\":\"AI_REQUEST_FAILED\"");
    }

    private ModelChecklistResearch failedResearch() throws Exception {
        ModelChecklistResearch research = ModelChecklistResearch.start(201L, 1);
        research.fail(objectMapper.writeValueAsString(
                ChecklistSupplementResult.requestFailed()));
        ReflectionTestUtils.setField(research, "id", 501L);
        return research;
    }

    private Category laptop() {
        Category parent =
                Category.createTopLevel("노트북", DeviceType.LAPTOP, 1);
        Category model = Category.createLeaf(
                parent,
                "Galaxy Book4",
                DeviceType.LAPTOP,
                "Samsung",
                OsFamily.WINDOWS,
                "NT960",
                List.of(512),
                1);
        ReflectionTestUtils.setField(model, "id", 201L);
        return model;
    }
}
