package com.c203.limit.domain.inspection.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.dto.response.OcrResultItemResponse;
import com.c203.limit.domain.inspection.dto.response.OcrResultResponse;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.OcrExtractionStatus;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.service.OcrExtractionService;
import com.c203.limit.global.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InspectionOcrControllerTests {

    private static final Long EVIDENCE_ID = 9003L;
    private static final Long SELLER_ID = 1L;

    @Mock OcrExtractionService ocrExtractionService;
    @Mock CurrentUser currentUser;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InspectionOcrController(ocrExtractionService, currentUser))
                        .build();
    }

    @Test
    void returns201WithStructuredOcrResult() throws Exception {
        OcrResult ocrResult =
                OcrResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .fieldType(OcrFieldType.MODEL_NAME)
                        .rawText("Galaxy Book4 Pro")
                        .parsedValue("Galaxy Book4 Pro")
                        .confidence(new BigDecimal("0.900"))
                        .ocrModelVersion("mock-v1")
                        .detectedAt(LocalDateTime.parse("2026-07-27T10:15:00"))
                        .build();
        ReflectionTestUtils.setField(ocrResult, "id", 1L);
        OcrResultResponse response =
                OcrResultResponse.of(
                        EVIDENCE_ID,
                        List.of(OcrResultItemResponse.from(ocrResult)),
                        OcrExtractionStatus.PARTIAL,
                        java.util.Set.of(
                                OcrFieldType.STORAGE_CAPACITY,
                                OcrFieldType.GPU,
                                OcrFieldType.OS_VERSION));

        when(currentUser.memberId()).thenReturn(SELLER_ID);
        when(ocrExtractionService.extractAndStructure(EVIDENCE_ID, SELLER_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/inspections/evidence/{evidenceId}/ocr-results", EVIDENCE_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evidenceId").value(EVIDENCE_ID))
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.missingFieldTypes", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.data.results[0].ocrResultId").value(1))
                .andExpect(jsonPath("$.data.results[0].fieldType").value("MODEL_NAME"))
                .andExpect(jsonPath("$.data.results[0].parsedValue").value("Galaxy Book4 Pro"))
                .andExpect(jsonPath("$.data.results[0].confidence").value(0.900))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
