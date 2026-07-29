package com.c203.limit.domain.inspection.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.domain.inspection.service.ReinspectionRequestService;
import com.c203.limit.global.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReinspectionRequestControllerTests {
    @Mock ReinspectionRequestService service;
    @Mock CurrentUser currentUser;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReinspectionRequestController(service, currentUser))
                .build();
    }

    @Test
    void createsReinspectionRequest() throws Exception {
        when(currentUser.memberId()).thenReturn(30L);
        when(service.create(eq(10L), eq(30L), any())).thenReturn(response("REQUESTED", null));

        mockMvc.perform(post("/api/v1/listings/10/reinspection-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason": "외관을 다시 확인해 주세요",
                                  "items": [
                                    {"checklistItemId": 101, "requestContent": "모서리 확대"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestKey").value("request-key"))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.items[0].checklistItemId").value(101));
    }

    @Test
    void completesReinspectionRequest() throws Exception {
        when(currentUser.memberId()).thenReturn(40L);
        when(service.complete("request-key", 40L))
                .thenReturn(response("COMPLETED", LocalDateTime.of(2026, 7, 29, 11, 0)));

        mockMvc.perform(post("/api/v1/reinspection-requests/request-key/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists());
    }

    private ReinspectionRequestResponse response(String status, LocalDateTime completedAt) {
        return new ReinspectionRequestResponse(
                "request-key",
                status,
                "외관을 다시 확인해 주세요",
                List.of(new ReinspectionRequestResponse.Item(
                        101L, "외관", "모서리 확대", 0)),
                LocalDateTime.of(2026, 7, 29, 10, 0),
                completedAt);
    }
}
