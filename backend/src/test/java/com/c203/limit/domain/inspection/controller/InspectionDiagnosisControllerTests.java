package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldListResponse;
import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldResponse;
import com.c203.limit.domain.inspection.service.DiagnosisAggregationService;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InspectionDiagnosisControllerTests {

    private static final Long ITEM_ID = 1L;
    private static final Long SELLER_ID = 100L;

    @Mock DiagnosisAggregationService diagnosisAggregationService;
    @Mock CurrentUser currentUser;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new InspectionDiagnosisController(diagnosisAggregationService, currentUser))
                        .build();
    }

    @Test
    void returns200WithAggregatedDiagnosisFields() throws Exception {
        DiagnosisFieldListResponse response =
                new DiagnosisFieldListResponse(
                        ITEM_ID,
                        List.of(
                                new DiagnosisFieldResponse("CPU", "i7-13700H", "i7-13700H", false, null),
                                new DiagnosisFieldResponse("RAM", "16GB", "32768MB RAM", true, null)));
        when(currentUser.memberId()).thenReturn(SELLER_ID);
        when(diagnosisAggregationService.getDiagnosis(eq(ITEM_ID), eq(SELLER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(ITEM_ID))
                .andExpect(jsonPath("$.data.fields[0].fieldName").value("CPU"))
                .andExpect(jsonPath("$.data.fields[0].conflict").value(false))
                .andExpect(jsonPath("$.data.fields[1].fieldName").value("RAM"))
                .andExpect(jsonPath("$.data.fields[1].ocrValue").value("16GB"))
                .andExpect(jsonPath("$.data.fields[1].fileParseValue").value("32768MB RAM"))
                .andExpect(jsonPath("$.data.fields[1].conflict").value(true))
                .andExpect(jsonPath("$.data.fields[1].confirmedValue").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
