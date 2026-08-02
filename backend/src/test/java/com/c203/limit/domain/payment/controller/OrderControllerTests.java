package com.c203.limit.domain.payment.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.payment.dto.response.OrderSummaryResponse;
import com.c203.limit.domain.payment.service.OrderQueryService;
import com.c203.limit.global.security.CurrentUser;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OrderControllerTests {
    private static final Long BUYER_ID = 2L;

    @Mock OrderQueryService orderQueryService;
    @Mock CurrentUser currentUser;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderQueryService, currentUser)).build();
        when(currentUser.memberId()).thenReturn(BUYER_ID);
    }

    @Test
    void listOrdersReturns200WithOrderList() throws Exception {
        OrderSummaryResponse order = new OrderSummaryResponse(
                500L,
                100L,
                "갤럭시 S24",
                "https://cdn/thumb.jpg",
                BigDecimal.valueOf(650_000),
                "APPROVED",
                "PAID",
                OffsetDateTime.now(),
                OffsetDateTime.now());
        when(orderQueryService.listOrders(eq(BUYER_ID))).thenReturn(List.of(order));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].paymentId").value(500))
                .andExpect(jsonPath("$.data[0].productName").value("갤럭시 S24"));
    }

    @Test
    void listOrdersReturns200WithEmptyList() throws Exception {
        when(orderQueryService.listOrders(eq(BUYER_ID))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
