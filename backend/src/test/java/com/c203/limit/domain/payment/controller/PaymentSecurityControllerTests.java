package com.c203.limit.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.repository.MemberRestrictionRepository;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 결제 API가 실제 Security 필터 체인을 거칠 때의 인증·인가·공통 실패 포맷 계약을 검증한다.
 * {@link PaymentControllerTests}는 standaloneSetup이라 이 계약을 확인하지 못한다.
 */
@SpringBootTest(
        properties = {
            "management.endpoint.health.validate-group-membership=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                    + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                    + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
        })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PaymentSecurityControllerTests {

    private static final Long BUYER_ID = 2L;

    @MockitoBean com.c203.limit.domain.rtc.service.RtcCallService rtcCallService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean MemberTermsAgreementRepository memberTermsAgreementRepository;
    @MockitoBean SocialAccountRepository socialAccountRepository;
    @MockitoBean AdminAccountRepository adminAccountRepository;
    @MockitoBean AdminActionLogRepository adminActionLogRepository;
    @MockitoBean MemberRestrictionRepository memberRestrictionRepository;
    @MockitoBean ChatRoomRepository chatRoomRepository;
    @MockitoBean ChatRoomParticipantRepository chatRoomParticipantRepository;
    @MockitoBean ChatMessageRepository chatMessageRepository;
    @MockitoBean ListingChatReader listingChatReader;
    @MockitoBean ListingRepository listingRepository;
    @MockitoBean ListingStatusHistoryRepository listingStatusHistoryRepository;
    @MockitoBean WishlistRepository wishlistRepository;
    @MockitoBean ProductApplicationService productApplicationService;
    @MockitoBean ProductCatalogService productCatalogService;
    @MockitoBean PaymentService paymentService;

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
    void createPaymentWithValidTokenReturnsCreated() throws Exception {
        when(paymentService.request(anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                new com.c203.limit.domain.payment.dto.response.PaymentResponse(
                                        500L,
                                        100L,
                                        "REQUESTED",
                                        PaymentMethod.CARD.name(),
                                        1,
                                        java.math.BigDecimal.valueOf(650_000),
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
