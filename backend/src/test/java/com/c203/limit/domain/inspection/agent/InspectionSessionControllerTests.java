package com.c203.limit.domain.inspection.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
