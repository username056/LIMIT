package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.dto.request.DiagnosisValueUpdateRequest;
import com.c203.limit.domain.inspection.dto.response.DiagnosisValueUpdateResponse;
import com.c203.limit.domain.inspection.service.DiagnosisValueConfirmationService;
import com.c203.limit.global.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InspectionDiagnosisValueControllerTests {

    private static final Long ITEM_ID = 2L;
    private static final Long SELLER_ID = 100L;

    @Mock DiagnosisValueConfirmationService diagnosisValueConfirmationService;
    @Mock CurrentUser currentUser;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new InspectionDiagnosisValueController(diagnosisValueConfirmationService, currentUser))
                        .build();
    }

    @Test
    void returns200WithConfirmedValue() throws Exception {
        DiagnosisValueUpdateResponse response =
                new DiagnosisValueUpdateResponse(
                        ITEM_ID, "CPU", "FILE CPU", "confirmed CPU", LocalDateTime.parse("2026-07-28T10:15:00"));
        when(currentUser.memberId()).thenReturn(SELLER_ID);
        when(diagnosisValueConfirmationService.confirm(eq(ITEM_ID), eq(SELLER_ID), any())).thenReturn(response);

        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("CPU", "confirmed CPU");

        mockMvc.perform(
                        patch("/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis-values", ITEM_ID)
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(ITEM_ID))
                .andExpect(jsonPath("$.data.fieldName").value("CPU"))
                .andExpect(jsonPath("$.data.originalValue").value("FILE CPU"))
                .andExpect(jsonPath("$.data.confirmedValue").value("confirmed CPU"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
