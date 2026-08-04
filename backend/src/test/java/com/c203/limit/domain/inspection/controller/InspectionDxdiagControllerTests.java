package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.service.DxdiagParsingService;
import com.c203.limit.global.security.CurrentUser;
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
class InspectionDxdiagControllerTests {

    private static final Long EVIDENCE_ID = 9004L;
    private static final Long SELLER_ID = 100L;

    @Mock DxdiagParsingService dxdiagParsingService;
    @Mock CurrentUser currentUser;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InspectionDxdiagController(dxdiagParsingService, currentUser))
                        .build();
    }

    @Test
    void returns201WithParsedDxdiagResult() throws Exception {
        DxdiagResult entity =
                DxdiagResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .modelName("950XDB")
                        .osVersion("Windows 11 Pro")
                        .storageCapacity("475.8 GB")
                        .cpu("11th Gen Intel(R) Core(TM) i7-1165G7")
                        .memory("16384 MB RAM")
                        .gpu("Intel(R) Iris(R) Xe Graphics")
                        .gpuMemory("8156 MB")
                        .driverVersion("27.20.100.9415")
                        .soundDevice("스피커(Realtek(R) Audio)")
                        .parserVersion("dxdiag-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.parse("2026-07-23T20:55:12"))
                        .build();
        ReflectionTestUtils.setField(entity, "id", 1L);
        when(currentUser.memberId()).thenReturn(SELLER_ID);
        when(dxdiagParsingService.parse(eq(EVIDENCE_ID), eq(SELLER_ID))).thenReturn(entity);

        mockMvc.perform(post("/api/v1/inspections/evidence/{evidenceId}/dxdiag-results", EVIDENCE_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dxdiagResultId").value(1))
                .andExpect(jsonPath("$.data.modelName").value("950XDB"))
                .andExpect(jsonPath("$.data.osVersion").value("Windows 11 Pro"))
                .andExpect(jsonPath("$.data.storageCapacity").value("475.8 GB"))
                .andExpect(jsonPath("$.data.cpu").value("11th Gen Intel(R) Core(TM) i7-1165G7"))
                .andExpect(jsonPath("$.data.memory").value("16384 MB RAM"))
                .andExpect(jsonPath("$.data.gpu").value("Intel(R) Iris(R) Xe Graphics"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.missingFields").isEmpty())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void returns201WithMissingFieldsWhenPartial() throws Exception {
        DxdiagResult entity =
                DxdiagResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .modelName("950XDB")
                        .osVersion("Windows 11 Pro")
                        .storageCapacity("475.8 GB")
                        .cpu("11th Gen Intel(R) Core(TM) i7-1165G7")
                        .memory("16384 MB RAM")
                        .parserVersion("dxdiag-v1")
                        .parseStatus(ParseStatus.PARTIAL)
                        .parsedAt(LocalDateTime.parse("2026-07-23T20:55:12"))
                        .build();
        ReflectionTestUtils.setField(entity, "id", 2L);
        when(currentUser.memberId()).thenReturn(SELLER_ID);
        when(dxdiagParsingService.parse(eq(EVIDENCE_ID), eq(SELLER_ID))).thenReturn(entity);

        mockMvc.perform(post("/api/v1/inspections/evidence/{evidenceId}/dxdiag-results", EVIDENCE_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.missingFields")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "gpu", "gpuMemory", "driverVersion", "soundDevice")));
    }
}
