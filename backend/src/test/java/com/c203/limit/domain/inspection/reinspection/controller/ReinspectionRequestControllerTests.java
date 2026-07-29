package com.c203.limit.domain.inspection.reinspection.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestItemCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.response.ReinspectionRequestItemResponse;
import com.c203.limit.domain.inspection.reinspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.domain.inspection.reinspection.service.ReinspectionRequestService;
import com.c203.limit.global.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final Long LISTING_ID = 10L;
    private static final Long BUYER_ID = 100L;
    private static final Long SELLER_ID = 200L;
    private static final String REQUEST_KEY = "b3b1c7a2-3c1a-4b1a-9c1a-1a2b3c4d5e6f";

    @Mock ReinspectionRequestService reinspectionRequestService;
    @Mock CurrentUser currentUser;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ReinspectionRequestController(reinspectionRequestService, currentUser))
                        .build();
    }

    private ReinspectionRequestResponse sampleResponse(String status) {
        ReinspectionRequestItemResponse item =
                new ReinspectionRequestItemResponse(301L, "제품 외관", "모서리를 가까이 촬영해 주세요.", 1);
        return new ReinspectionRequestResponse(
                1L,
                REQUEST_KEY,
                LISTING_ID,
                25L,
                BUYER_ID,
                SELLER_ID,
                "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
                status,
                LocalDateTime.parse("2026-07-22T14:30:00"),
                null,
                List.of(item));
    }

    @Test
    void returns201WithCreatedRequest() throws Exception {
        when(currentUser.memberId()).thenReturn(BUYER_ID);
        when(reinspectionRequestService.request(eq(LISTING_ID), eq(BUYER_ID), any()))
                .thenReturn(sampleResponse("REQUESTED"));

        ReinspectionRequestCreateRequest request =
                new ReinspectionRequestCreateRequest(
                        "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
                        List.of(new ReinspectionRequestItemCreateRequest(301L, "모서리를 가까이 촬영해 주세요.")));

        mockMvc.perform(
                        post("/api/v1/listings/{listingId}/reinspection-requests", LISTING_ID)
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestKey").value(REQUEST_KEY))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.items[0].itemName").value("제품 외관"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void returns200WithCompletedRequest() throws Exception {
        when(currentUser.memberId()).thenReturn(SELLER_ID);
        when(reinspectionRequestService.complete(REQUEST_KEY, SELLER_ID)).thenReturn(sampleResponse("COMPLETED"));

        mockMvc.perform(post("/api/v1/reinspection-requests/{requestKey}/complete", REQUEST_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
