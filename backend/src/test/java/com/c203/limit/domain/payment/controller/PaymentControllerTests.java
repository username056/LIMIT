package com.c203.limit.domain.payment.controller;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.global.security.CurrentUser;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTests {
    private static final Long BUYER_ID = 2L;
    private static final Long LISTING_ID = 100L;
    private static final Long PAYMENT_ID = 500L;

    @Mock PaymentService paymentService;
    @Mock CurrentUser currentUser;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new PaymentController(paymentService, currentUser))
                        .build();
        lenient().when(currentUser.memberId()).thenReturn(BUYER_ID);
    }

    private PaymentResponse response() {
        return new PaymentResponse(
                PAYMENT_ID,
                LISTING_ID,
                "PAY-test-order-1",
                "REQUESTED",
                "CARD",
                1,
                BigDecimal.valueOf(650_000),
                null,
                null,
                OffsetDateTime.now(),
                null);
    }

    @Test
    void createPaymentReturns201() throws Exception {
        when(paymentService.request(org.mockito.ArgumentMatchers.eq(BUYER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response());

        mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "listingId": 100,
                                          "method": "CARD",
                                          "idempotencyKey": "idem-1"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));
    }

    @Test
    void createPaymentRejectsBlankIdempotencyKey() throws Exception {
        mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "listingId": 100,
                                          "method": "CARD",
                                          "idempotencyKey": ""
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmPaymentReturns200() throws Exception {
        when(paymentService.confirm(
                        org.mockito.ArgumentMatchers.eq(BUYER_ID),
                        org.mockito.ArgumentMatchers.eq(PAYMENT_ID),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(response());

        mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/confirm", PAYMENT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "paymentKey": "payment-key-1",
                                          "orderId": "PAY-test-order-1",
                                          "amount": 650000
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID));
    }

    @Test
    void retryPaymentReturns200() throws Exception {
        when(paymentService.retryAttempt(
                        org.mockito.ArgumentMatchers.eq(BUYER_ID),
                        org.mockito.ArgumentMatchers.eq(PAYMENT_ID),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(response());

        mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/retry", PAYMENT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "method": "TOSSPAY"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID));
    }

    @Test
    void cancelPaymentReturns200() throws Exception {
        when(paymentService.cancel(BUYER_ID, PAYMENT_ID)).thenReturn(response());

        mockMvc.perform(post("/api/v1/payments/{paymentId}/cancel", PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));
    }

    @Test
    void getPaymentReturnsDetail() throws Exception {
        when(paymentService.get(BUYER_ID, PAYMENT_ID)).thenReturn(response());

        mockMvc.perform(get("/api/v1/payments/{paymentId}", PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.data.listingId").value(LISTING_ID));
    }
}
