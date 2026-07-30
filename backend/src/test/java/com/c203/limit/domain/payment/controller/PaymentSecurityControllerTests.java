package com.c203.limit.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.global.config.JacksonConfig;
import com.c203.limit.global.config.SecurityConfig;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.CurrentUser;
import com.c203.limit.global.security.JwtAuthenticationFilter;
import com.c203.limit.global.security.JwtTokenProvider;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 결제 API가 실제 Security 필터 체인을 거칠 때의 인증·인가·공통 실패 포맷 계약을 검증한다.
 * {@link PaymentControllerTests}는 standaloneSetup이라 이 계약을 확인하지 못한다.
 *
 * <p>{@code PaymentController}만 슬라이스로 띄운다. 예전에는 {@code @SpringBootTest}로 앱 전체
 * 컨텍스트를 로드하면서 JPA를 꺼버려, 무관한 도메인이 리포지토리를 하나만 추가해도(예: 채팅 미디어,
 * 판매자, 검수 OCR) 이 파일에 mock을 추가해야만 컨텍스트가 뜨는 문제가 반복됐다. Security
 * 설정(`SecurityConfig`, `JwtAuthenticationFilter`, `CurrentUser`, `JwtTokenProvider`)은
 * 리포지토리에 의존하지 않으므로, 이들만 명시적으로 가져오면 다른 도메인과 완전히 무관해진다.
 *
 * <p>{@code JwtAuthenticationFilter}는 {@code SecurityConfig}가 {@code HttpSecurity}에
 * {@code addFilterBefore}로 직접 등록하는데, 이 빈이 동시에 일반 서블릿 {@code Filter} 빈으로도
 * 보여서 {@code @WebMvcTest}의 MockMvc가 Security 체인과는 별개로 한 번 더 최상위에 등록해버린다.
 * 두 번째 실행에서 컨텍스트가 초기화돼 인증이 사라지므로, 운영 앱의 Security 자동설정이 하는 것과
 * 동일하게 최상위 등록만 비활성화한다.
 */
@WebMvcTest(controllers = PaymentController.class)
@EnableWebSecurity
@Import({
    JacksonConfig.class,
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    CurrentUser.class,
    JwtTokenProvider.class
})
class PaymentSecurityControllerTests {

    @TestConfiguration
    static class DisableTopLevelJwtFilterRegistrationConfig {
        @Bean
        FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
                JwtAuthenticationFilter filter) {
            FilterRegistrationBean<JwtAuthenticationFilter> registration =
                    new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }

    private static final Long BUYER_ID = 2L;

    @MockitoBean PaymentService paymentService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider tokens;

    private String memberBearer(Long memberId) {
        return "Bearer " + tokens.issueAccess(memberId, "MEMBER", Set.of("MEMBER")).value();
    }

    @Test
    void createPaymentWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(
                        post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"listingId": 100, "method": "CARD", "idempotencyKey": "idem-1"}
                                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmPaymentWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/confirm", 500L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"paymentKey": "payment-key-1", "orderId": "PAY-500-1", "amount": 650000}
                                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmPaymentReturnsUnprocessableEntityWithCommonEnvelopeWhenTossRejects() throws Exception {
        when(paymentService.confirm(org.mockito.ArgumentMatchers.eq(BUYER_ID), org.mockito.ArgumentMatchers.eq(500L), any()))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_CONFIRM_REJECTED, "카드 승인이 거절되었습니다."));

        mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/confirm", 500L)
                                .header("Authorization", memberBearer(BUYER_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"paymentKey": "payment-key-1", "orderId": "PAY-500-1", "amount": 650000}
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PAY012"))
                .andExpect(jsonPath("$.error.message").value("카드 승인이 거절되었습니다."))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void getPaymentWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{paymentId}", 500L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPaymentReturnsForbiddenForNonOwnerWithCommonEnvelope() throws Exception {
        when(paymentService.get(BUYER_ID, 500L))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/payments/{paymentId}", 500L).header("Authorization", memberBearer(BUYER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PAY003"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void getPaymentReturnsNotFoundWhenPaymentMissing() throws Exception {
        when(paymentService.get(BUYER_ID, 999L))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/payments/{paymentId}", 999L).header("Authorization", memberBearer(BUYER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAY001"));
    }

    @Test
    void createPaymentReturnsConflictWithCommonEnvelopeWhenIdempotencyKeyConflicts() throws Exception {
        when(paymentService.request(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));

        mockMvc.perform(
                        post("/api/v1/payments")
                                .header("Authorization", memberBearer(BUYER_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"listingId": 100, "method": "CARD", "idempotencyKey": "idem-1"}
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAY004"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void createPaymentReturnsConflictWithCommonEnvelopeWhenLockRetriesExhausted() throws Exception {
        when(paymentService.request(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT));

        mockMvc.perform(
                        post("/api/v1/payments")
                                .header("Authorization", memberBearer(BUYER_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"listingId": 100, "method": "CARD", "idempotencyKey": "idem-1"}
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAY005"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void createPaymentWithValidTokenReturnsCreated() throws Exception {
        when(paymentService.request(anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                new com.c203.limit.domain.payment.dto.response.PaymentResponse(
                                        500L,
                                        100L,
                                        "PAY-test-order-1",
                                        "REQUESTED",
                                        PaymentMethod.CARD.name(),
                                        1,
                                        java.math.BigDecimal.valueOf(650_000),
                                        null,
                                        null,
                                        java.time.OffsetDateTime.now(),
                                        null));

        mockMvc.perform(
                        post("/api/v1/payments")
                                .header("Authorization", memberBearer(BUYER_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"listingId": 100, "method": "CARD", "idempotencyKey": "idem-1"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentId").value(500L));
    }
}
