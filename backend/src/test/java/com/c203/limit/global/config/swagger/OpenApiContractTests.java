package com.c203.limit.global.config.swagger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.repository.MemberRestrictionRepository;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.domain.payment.service.PaymentService;

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
class OpenApiContractTests {

    @MockitoBean
    com.c203.limit.domain.rtc.service.RtcCallService rtcCallService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    MemberRepository memberRepository;

    @MockitoBean
    MemberTermsAgreementRepository memberTermsAgreementRepository;

    @MockitoBean
    SocialAccountRepository socialAccountRepository;

    @MockitoBean
    AdminAccountRepository adminAccountRepository;

    @MockitoBean
    AdminActionLogRepository adminActionLogRepository;

    @MockitoBean
    MemberRestrictionRepository memberRestrictionRepository;

    @MockitoBean
    ChatRoomRepository chatRoomRepository;

    @MockitoBean
    ChatRoomParticipantRepository chatRoomParticipantRepository;

    @MockitoBean
    ChatMessageRepository chatMessageRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatMediaRepository chatMediaRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatMessageMediaRepository chatMessageMediaRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatRoomContextReader chatRoomContextReader;

    @MockitoBean
    ListingChatReader listingChatReader;

    @MockitoBean
    ListingRepository listingRepository;

    @MockitoBean
    WishlistRepository wishlistRepository;

    @MockitoBean
    ProductApplicationService productApplicationService;

    @MockitoBean
    ProductCatalogService productCatalogService;

    @MockitoBean
    com.c203.limit.domain.inspection.checklist.ChecklistGenerationService
            checklistGenerationService;

    @MockitoBean
    ListingStatusHistoryRepository listingStatusHistoryRepository;

    @MockitoBean
    PaymentService paymentService;

    @MockitoBean
    com.c203.limit.domain.seller.repository.SellerRepository sellerRepository;

    @MockitoBean
    EvidenceRepository evidenceRepository;

    @MockitoBean
    OcrResultRepository ocrResultRepository;

    @MockitoBean
    ListingOwnerReader listingOwnerReader;

    @MockitoBean
    ListingChecklistItemRepository listingChecklistItemRepository;

    @MockitoBean
    DxdiagResultRepository dxdiagResultRepository;

    @MockitoBean
    BatteryReportResultRepository batteryReportResultRepository;

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesOpenApiBaseContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.info.title").value("Limit API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.internalApiKey").exists())
                .andExpect(jsonPath("$.components.schemas.ApiResponse").exists())
                .andExpect(jsonPath("$.paths['/api/v1/health']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.summary").value("애플리케이션 상태 확인"))
                .andExpect(jsonPath("$.components.schemas.ChatRoomResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CursorResponse").exists())
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms/{roomId}/messages'].get.operationId")
                        .value("chatBe06"))
                .andExpect(jsonPath("$.paths['/api/v1/members'].post.operationId").value("auth03"))
                .andExpect(jsonPath("$.paths['/api/v1/members/me'].get.operationId").value("member01"))
                .andExpect(jsonPath("$.components.schemas.SignupRequest").exists())
                .andExpect(jsonPath("$.components.schemas.CompleteSocialSignupRequest").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/social-authorizations/{provider}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/social-signups']").exists())
                .andExpect(jsonPath("$.components.schemas.EmailVerificationRequest").exists())
                .andExpect(jsonPath("$.components.schemas.PasswordResetRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ResetPasswordRequest").exists())
                .andExpect(jsonPath("$.components.schemas.AdminLoginRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ChangeAdminPasswordRequest").exists())
                .andExpect(jsonPath("$.components.schemas.MemberProfileResponse").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/members']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/me/password']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-reset-requests']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-resets']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.operationId").value("product01"))
                .andExpect(jsonPath("$.paths['/api/v1/checklist-generations'].post.operationId")
                        .value("productChecklist01"))
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.operationId").value("product04"))
                .andExpect(jsonPath("$.components.schemas.CreateProductRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ProductDetailResponse").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sellers'].post.operationId")
                        .value("seller01"))
                .andExpect(jsonPath("$.paths['/api/v1/sellers/me'].get.operationId")
                        .value("seller02"))
                .andExpect(jsonPath("$.components.schemas.CreateSellerRequest").exists())
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms/{roomId}/calls'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/calls/{callId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/calls/{callId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/rtc-sessions/{sessionId}/join'].post").exists())
                .andExpect(jsonPath("$.components.schemas.EndRtcSessionRequest").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.operationId").value("payment01"))
                .andExpect(jsonPath("$.components.schemas.CreatePaymentRequest").exists())
                .andExpect(jsonPath("$.components.schemas.PaymentResponse").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/payments'].post.responses['409'].description")
                                .value(containsString("PAYMENT_REQUEST_CONFLICT")));
    }

    @Test
    void exposesSwaggerUiConfiguration() throws Exception {
        mockMvc.perform(get("/v3/api-docs/01-auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/members/me'].get").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/02-member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/members/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/03-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/members']").exists())
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/v1/admin/members'].get.security[0].bearerAuth")
                                .exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/admin/sessions'].post.security")
                                .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/members/me'].get").doesNotExist());
        mockMvc.perform(get("/v3/api-docs/04-chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/listings/{listingId}/chat-rooms'].post.operationId")
                        .value("chatBe01"))
                .andExpect(jsonPath("$.paths['/api/v1/listings/{listingId}/chat-rooms'].post.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms'].get.operationId")
                        .value("chatBe07"))
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms/{roomId}/messages'].get.operationId")
                        .value("chatBe06"))
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms'].get.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/05-product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.operationId")
                        .value("product01"))
                .andExpect(jsonPath("$.paths['/api/v1/device-models/{deviceModelId}/checklist-template'].get.operationId")
                        .value("checklist01"))
                .andExpect(jsonPath("$.paths['/api/v1/products/{productId}/checklist-items'].get.operationId")
                        .value("checklist02"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/06-rtc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms/{roomId}/calls'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/rtc-sessions/{sessionId}/join'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/07-seller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/sellers'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sellers/me'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/08-payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.operationId").value("payment01"))
                .andExpect(jsonPath("$.paths['/api/v1/payments/{paymentId}'].get.operationId")
                        .value("payment02"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/09-inspection"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/inspections/evidence/{evidenceId}/ocr-results'].post.operationId")
                .value("ocr01"))
            .andExpect(jsonPath("$.paths['/api/v1/inspections/evidence/{evidenceId}/dxdiag-results'].post.operationId")
                .value("dxdiag01"))
            .andExpect(jsonPath("$.paths['/api/v1/inspections/evidence/{evidenceId}/battery-report-results'].post.operationId")
                .value("batteryReport01"))
            .andExpect(jsonPath("$.paths['/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis'].get.operationId")
                .value("diagnosis01"))
            .andExpect(jsonPath("$.paths['/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis-values'].patch.operationId")
                .value("diagnosisValue01"))
            .andExpect(jsonPath("$.paths['/api/v1/inspections/products/{productId}/diagnosis-summary'].get.operationId")
                .value("productDiagnosisSummary01"))
            .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/10-place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/places/search'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls.length()").value(10))
                .andExpect(jsonPath("$['urls.primaryName']").value("01-auth"))
                .andExpect(jsonPath("$.operationsSorter", containsString("post: 0")))
                .andExpect(jsonPath("$.operationsSorter", containsString("delete: 4")));
    }

    @Test
    void exposesSwaggerUiEntryPointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void exportsCurrentOpenApi() throws Exception {
        String output = System.getProperty("openapi.output");
        Assumptions.assumeTrue(output != null && !output.isBlank());

        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        Path outputPath = Path.of(output).toAbsolutePath().normalize();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(
                outputPath,
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }
}
