package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.service.BatteryReportParsingService;
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
class InspectionBatteryReportControllerTests {

    private static final Long EVIDENCE_ID = 9005L;

    @Mock BatteryReportParsingService batteryReportParsingService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InspectionBatteryReportController(batteryReportParsingService))
                        .build();
    }

    @Test
    void returns201WithParsedBatteryReportResult() throws Exception {
        BatteryReportResult entity =
                BatteryReportResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .batteryManufacturer("SAMSUNG Electronics")
                        .designCapacity("67,010 mWh")
                        .fullChargeCapacity("55,584 mWh")
                        .cycleCount(418)
                        .capacityRatio(new BigDecimal("82.95"))
                        .parserVersion("battery-report-dom-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.parse("2026-07-23T21:00:00"))
                        .build();
        ReflectionTestUtils.setField(entity, "id", 1L);
        when(batteryReportParsingService.parse(eq(EVIDENCE_ID))).thenReturn(entity);

        mockMvc.perform(
                        post(
                                "/api/v1/inspections/evidence/{evidenceId}/battery-report-results",
                                EVIDENCE_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.batteryReportResultId").value(1))
                .andExpect(jsonPath("$.data.evidenceId").value(EVIDENCE_ID))
                .andExpect(jsonPath("$.data.batteryManufacturer").value("SAMSUNG Electronics"))
                .andExpect(jsonPath("$.data.designCapacity").value("67,010 mWh"))
                .andExpect(jsonPath("$.data.cycleCount").value(418))
                .andExpect(jsonPath("$.data.capacityRatio").value(82.95))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
