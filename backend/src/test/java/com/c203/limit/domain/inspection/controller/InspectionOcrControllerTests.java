package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.service.OcrExtractionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Mock OcrExtractionService ocrExtractionService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InspectionOcrController(ocrExtractionService)).build();
    }

    @Test
    void returns201WithExtractedOcrResult() throws Exception {
        OcrResult ocrResult =
                OcrResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .fieldType(OcrFieldType.MODEL_NAME)
                        .rawText("Galaxy Book4 Pro")
                        .parsedValue("Galaxy Book4 Pro")
                        .confidence(new BigDecimal("0.965"))
                        .ocrModelVersion("V2")
                        .detectedAt(LocalDateTime.parse("2026-07-23T10:15:00"))
                        .build();
        ReflectionTestUtils.setField(ocrResult, "id", 1L);
        when(ocrExtractionService.extractText(eq(EVIDENCE_ID), eq(OcrFieldType.MODEL_NAME)))
                .thenReturn(ocrResult);

        mockMvc.perform(
                        post("/api/v1/inspections/evidence/{evidenceId}/ocr-results", EVIDENCE_ID)
                                .contentType("application/json")
                                .content("{\"fieldType\":\"MODEL_NAME\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ocrResultId").value(1))
                .andExpect(jsonPath("$.data.evidenceId").value(EVIDENCE_ID))
                .andExpect(jsonPath("$.data.fieldType").value("MODEL_NAME"))
                .andExpect(jsonPath("$.data.rawText").value("Galaxy Book4 Pro"))
                .andExpect(jsonPath("$.data.confidence").value(0.965))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
