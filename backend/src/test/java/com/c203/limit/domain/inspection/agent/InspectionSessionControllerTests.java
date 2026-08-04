package com.c203.limit.domain.inspection.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.TestResultSubmission;
import com.c203.limit.global.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InspectionSessionControllerTests {

    @Mock InspectionSessionService service;
    @Mock CurrentUser currentUser;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InspectionSessionController(service, currentUser))
                        .build();
    }

    @Test
    void rejectsTestResultSubmissionMissingRequiredFields() throws Exception {
        // clientResultId, testType, measurementStatus, testedAt가 모두 빠진 요청.
        mockMvc.perform(
                        post("/api/v1/inspection-agent/sessions/{sessionKey}/test-results", "session-1")
                                .header("Authorization", "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsCreatedForFirstTestResultSubmission() throws Exception {
        when(service.submitTestResult(eq("Bearer token"), eq("session-1"), any()))
                .thenReturn(new TestResultSubmission(null, true));

        mockMvc.perform(
                        post("/api/v1/inspection-agent/sessions/{sessionKey}/test-results", "session-1")
                                .header("Authorization", "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    void returnsOkForIdempotentReplay() throws Exception {
        when(service.submitTestResult(eq("Bearer token"), eq("session-1"), any()))
                .thenReturn(new TestResultSubmission(null, false));

        mockMvc.perform(
                        post("/api/v1/inspection-agent/sessions/{sessionKey}/test-results", "session-1")
                                .header("Authorization", "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                .andExpect(status().isOk());
    }

    private String validRequest() {
        return """
                {
                  "clientResultId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
                  "testType": "CAMERA",
                  "measurementStatus": "DETECTED",
                  "userResult": "USER_CONFIRMED",
                  "testedAt": "2026-08-03T01:00:00Z"
                }
                """;
    }
}
