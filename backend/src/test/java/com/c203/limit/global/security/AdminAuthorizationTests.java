package com.c203.limit.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.util.ReflectionTestUtils;
import com.c203.limit.domain.admin.repository.*;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ChatOutboxEventRepository;
import com.c203.limit.domain.chat.repository.ReinspectionRequestMessageRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.ListingImageRepository;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.domain.seller.entity.Seller;

@SpringBootTest(properties = {
        "management.endpoint.health.validate-group-membership=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminAuthorizationTests {

    @MockitoBean
    com.c203.limit.domain.rtc.service.RtcCallService rtcCallService;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean MemberRepository members;
    @MockitoBean MemberTermsAgreementRepository memberTermsAgreements;
    @MockitoBean SocialAccountRepository socialAccounts;
    @MockitoBean AdminAccountRepository admins;
    @MockitoBean MemberRestrictionRepository restrictions;
    @MockitoBean AdminActionLogRepository logs;
    @MockitoBean ChatRoomRepository chatRoomRepository;
    @MockitoBean ChatRoomParticipantRepository chatRoomParticipantRepository;
    @MockitoBean ChatMessageRepository chatMessageRepository;
    @MockitoBean ChatOutboxEventRepository chatOutboxEventRepository;
    @MockitoBean ReinspectionRequestMessageRepository reinspectionRequestMessageRepository;
    @MockitoBean com.c203.limit.domain.chat.repository.ChatMediaRepository chatMediaRepository;
    @MockitoBean com.c203.limit.domain.chat.repository.ChatMessageMediaRepository chatMessageMediaRepository;
    @MockitoBean com.c203.limit.domain.chat.repository.ChatRoomContextReader chatRoomContextReader;
    // JdbcClient를 쓰는 리더는 DataSource 자동설정을 끈 이 컨텍스트에서 만들 수 없다.
    @MockitoBean com.c203.limit.domain.product.repository.ProductEngagementReader productEngagementReader;
    @MockitoBean ListingChatReader listingChatReader;
    @MockitoBean
    com.c203.limit.domain.payment.repository.ExpiredReservationCandidateReader
            expiredReservationCandidateReader;
    @MockitoBean ListingRepository listingRepository;
    @MockitoBean com.c203.limit.domain.product.repository.DeviceModelRepository deviceModelRepository;
    @MockitoBean
    com.c203.limit.domain.product.repository.DeviceVariantRepository deviceVariantRepository;
    @MockitoBean
    com.c203.limit.domain.product.repository.DeviceCategoryRepository deviceCategoryRepository;
    @MockitoBean
    com.c203.limit.domain.product.repository.ManufacturerRepository manufacturerRepository;
    @MockitoBean WishlistRepository wishlistRepository;
    @MockitoBean ProductApplicationService productApplicationService;
    @MockitoBean
    com.c203.limit.domain.product.moderation.service.ListingModerationService
            listingModerationService;
    @MockitoBean
    com.c203.limit.domain.product.moderation.service.ModerationRiskService moderationRiskService;
    @MockitoBean ProductCatalogService productCatalogService;
    @MockitoBean
    com.c203.limit.domain.inspection.checklist.ChecklistGenerationService
            checklistGenerationService;
    @MockitoBean ListingStatusHistoryRepository listingStatusHistoryRepository;
    @MockitoBean ListingImageRepository listingImageRepository;
    @MockitoBean MediaUploadSessionRepository mediaUploadSessionRepository;
    @MockitoBean PaymentService paymentService;
    @MockitoBean
    com.c203.limit.domain.payment.service.PaymentReservationExpirationService
            paymentReservationExpirationService;
    @MockitoBean
    com.c203.limit.domain.payment.repository.ListingOrderSummaryReader listingOrderSummaryReader;
    @MockitoBean com.c203.limit.domain.payment.repository.PaymentRepository paymentRepository;
    @MockitoBean EvidenceRepository evidenceRepository;
    @MockitoBean OcrResultRepository ocrResultRepository;
    @MockitoBean ListingOwnerReader listingOwnerReader;
    @MockitoBean ListingChecklistItemRepository listingChecklistItemRepository;

    @MockitoBean
    com.c203.limit.domain.inspection.agent.InspectionSessionRepository inspectionSessionRepository;
    @MockitoBean
    com.c203.limit.domain.inspection.agent.InspectionSessionTestResultRepository
            inspectionSessionTestResultRepository;
    @MockitoBean ReinspectionRequestRepository reinspectionRequestRepository;
    @MockitoBean ReinspectionRequestItemRepository reinspectionRequestItemRepository;
    @MockitoBean DxdiagResultRepository dxdiagResultRepository;
    @MockitoBean BatteryReportResultRepository batteryReportResultRepository;
    @MockitoBean com.c203.limit.domain.seller.repository.SellerRepository sellerRepository;
    @MockitoBean
    com.c203.limit.domain.inspection.service.ModelChecklistResearchService
            modelChecklistResearchService;
    @MockitoBean
    com.c203.limit.domain.product.service.DeviceModelRequestService deviceModelRequestService;

    @MockitoBean
    com.c203.limit.domain.product.service.DeviceModelManagementService deviceModelManagementService;

    @Test
    void publicHealthEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void prometheusEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void prometheusEndpointRejectsUnauthenticatedNonGetRequest() throws Exception {
        mockMvc.perform(post("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void memberCannotReadAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members").header("Authorization", memberBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotCreateProductWithoutSellerRole() throws Exception {
        mockMvc.perform(
                        post("/api/v1/products")
                                .header("Authorization", memberBearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "categoryId": 1,
                                          "deviceModelId": 101,
                                          "name": "판매 상품",
                                          "price": 100000,
                                          "tradeRegion": "서울"
                                        }
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleSellerTokenCannotCreateProductWithoutActiveSellerProfile() throws Exception {
        mockMvc.perform(
                        post("/api/v1/products")
                                .header("Authorization", sellerBearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "categoryId": 1,
                                          "deviceModelId": 101,
                                          "name": "판매 상품",
                                          "price": 100000,
                                          "tradeRegion": "서울"
                                        }
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SEL010"));
    }

    @Test
    void verifiedMemberCanRegisterAsActiveSellerWithoutApproval() throws Exception {
        Member member = Member.createLocal("seller@limit.local", "encoded", "seller", null);
        member.verifyEmail();
        ReflectionTestUtils.setField(member, "id", 1L);
        when(members.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(sellerRepository.saveAndFlush(any(Seller.class)))
                .thenAnswer(
                        invocation -> {
                            Seller seller = invocation.getArgument(0);
                            ReflectionTestUtils.setField(seller, "id", 12L);
                            return seller;
                        });

        mockMvc.perform(
                        post("/api/v1/sellers")
                                .header("Authorization", memberBearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "sellerType": "INDIVIDUAL",
                                          "countryCode": "KR",
                                          "settlementBankName": "국민은행",
                                          "settlementAccountHolder": "판매자",
                                          "settlementAccountLast4": "1234",
                                          "sellerTermsAccepted": true
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sellerId").value(12L))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void operatorCannotManageAdminAccounts() throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/accounts")
                                .header("Authorization", bearer("OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanReadAdminAccounts() throws Exception {
        when(admins.findAll(any(Pageable.class))).thenReturn(Page.empty());
        mockMvc.perform(
                        get("/api/v1/admin/accounts")
                                .header("Authorization", bearer("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void protectedAdminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicSignupReturnsWrappedResponse() throws Exception {
        when(members.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 10L);
            return member;
        });
        mockMvc.perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@limit.local","password":"Password123","nickname":"newrunner",
                                 "serviceTermsAccepted":true,"privacyTermsAccepted":true,
                                 "ageRequirementAccepted":true,"marketingAccepted":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").value(10L));
    }

    private String bearer(String role) {
        return "Bearer " + tokens.issueAccess(1L, "ADMIN", Set.of(role)).value();
    }

    private String memberBearer() {
        return "Bearer " + tokens.issueAccess(1L, "MEMBER", Set.of("MEMBER")).value();
    }

    private String sellerBearer() {
        return "Bearer "
                + tokens.issueAccess(1L, "MEMBER", Set.of("MEMBER", "SELLER")).value();
    }
}
