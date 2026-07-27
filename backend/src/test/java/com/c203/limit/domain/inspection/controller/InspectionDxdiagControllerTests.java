package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.DxdiagParseResult;
import com.c203.limit.domain.inspection.service.DxdiagParsingService;
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

    @Mock DxdiagParsingService dxdiagParsingService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InspectionDxdiagController(dxdiagParsingService)).build();
    }

    @Test
    void returns201WithParsedDxdiagResult() throws Exception {
        DxdiagResult entity =
                DxdiagResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .cpu("11th Gen Intel(R) Core(TM) i7-1165G7")
                        .memory("16384MB RAM")
                        .gpu("Intel(R) Iris(R) Xe Graphics")
                        .gpuMemory("8156 MB")
                        .driverVersion("27.20.100.9415")
                        .soundDevice("스피커(Realtek(R) Audio)")
                        .parserVersion("dxdiag-dom-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.parse("2026-07-23T20:55:12"))
                        .build();
        ReflectionTestUtils.setField(entity, "id", 1L);
        DxdiagParseResult parsed =
                new DxdiagParseResult(
                        "SAMSUNG ELECTRONICS CO., LTD.",
                        "950XDB/951XDB/950XDY",
                        "Windows 10 Pro 64-bit",
                        entity.getCpu(),
                        entity.getMemory(),
                        entity.getGpu(),
                        entity.getGpuMemory(),
                        entity.getDriverVersion(),
                        entity.getSoundDevice());
        when(dxdiagParsingService.parse(eq(EVIDENCE_ID)))
                .thenReturn(new DxdiagParsingService.DxdiagParsingResult(entity, parsed));

        mockMvc.perform(post("/api/v1/inspections/evidence/{evidenceId}/dxdiag-results", EVIDENCE_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dxdiagResultId").value(1))
                .andExpect(jsonPath("$.data.evidenceId").value(EVIDENCE_ID))
                .andExpect(jsonPath("$.data.manufacturer").value("SAMSUNG ELECTRONICS CO., LTD."))
                .andExpect(jsonPath("$.data.model").value("950XDB/951XDB/950XDY"))
                .andExpect(jsonPath("$.data.osVersion").value("Windows 10 Pro 64-bit"))
                .andExpect(jsonPath("$.data.cpu").value("11th Gen Intel(R) Core(TM) i7-1165G7"))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
