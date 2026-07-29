package com.c203.limit.domain.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.repository.MemberRestrictionRepository;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ChatOutboxEventRepository;
import com.c203.limit.domain.chat.repository.ReinspectionRequestMessageRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.ProductChecklistItemResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductApplicationService.ProductPage;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.domain.product.service.ProductChecklistService;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.global.security.JwtTokenProvider;

@SpringBootTest(properties = {
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
class ProductMockControllerTests {

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
    ChatOutboxEventRepository chatOutboxEventRepository;

    @MockitoBean
    ReinspectionRequestMessageRepository reinspectionRequestMessageRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatMediaRepository chatMediaRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatMessageMediaRepository chatMessageMediaRepository;

    @MockitoBean
    com.c203.limit.domain.chat.repository.ChatRoomContextReader chatRoomContextReader;

    @MockitoBean
    ListingChatReader listingChatReader;

    @MockitoBean
    com.c203.limit.domain.payment.repository.ExpiredReservationCandidateReader
            expiredReservationCandidateReader;

    @MockitoBean
    ListingRepository listingRepository;

    @MockitoBean
    ListingStatusHistoryRepository listingStatusHistoryRepository;

    @MockitoBean
    EvidenceRepository evidenceRepository;

    @MockitoBean
    OcrResultRepository ocrResultRepository;

    @MockitoBean
    ListingOwnerReader listingOwnerReader;

    @MockitoBean
    ListingChecklistItemRepository listingChecklistItemRepository;

    @MockitoBean
    ReinspectionRequestRepository reinspectionRequestRepository;

    @MockitoBean
    ReinspectionRequestItemRepository reinspectionRequestItemRepository;

    @MockitoBean
    DxdiagResultRepository dxdiagResultRepository;

    @MockitoBean
    BatteryReportResultRepository batteryReportResultRepository;

    @MockitoBean
    WishlistRepository wishlistRepository;

    @MockitoBean
    ProductApplicationService productApplicationService;

    @MockitoBean
    ProductCatalogService productCatalogService;

    @MockitoBean
    ProductChecklistService productChecklistService;

    @MockitoBean
    com.c203.limit.domain.inspection.checklist.ChecklistGenerationService
            checklistGenerationService;

    @MockitoBean
    PaymentService paymentService;

    @MockitoBean
    com.c203.limit.domain.payment.repository.PaymentRepository paymentRepository;

    @MockitoBean
    com.c203.limit.domain.seller.repository.SellerRepository sellerRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider tokens;

    @Test
    void returnsPublicProductListMock() throws Exception {
        when(productApplicationService.findPublic(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        20,
                        "createdAt,desc"))
                .thenReturn(new ProductPage(List.of(), 0, 20, 0, 0, false));
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void forwardsPublicProductFiltersAndSort() throws Exception {
        when(productApplicationService.findPublic(
                        "Galaxy",
                        1L,
                        2L,
                        101L,
                        BigDecimal.valueOf(100000),
                        BigDecimal.valueOf(900000),
                        "서울",
                        "COMPLETED",
                        1,
                        10,
                        "price,asc"))
                .thenReturn(new ProductPage(List.of(), 1, 10, 0, 0, false));

        mockMvc.perform(get("/api/v1/products")
                        .param("keyword", "Galaxy")
                        .param("categoryId", "1")
                        .param("manufacturerId", "2")
                        .param("deviceModelId", "101")
                        .param("minPrice", "100000")
                        .param("maxPrice", "900000")
                        .param("tradeRegion", "서울")
                        .param("verificationStatus", "COMPLETED")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "price,asc"))
                .andExpect(status().isOk());

        verify(productApplicationService).findPublic(
                "Galaxy",
                1L,
                2L,
                101L,
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(900000),
                "서울",
                "COMPLETED",
                1,
                10,
                "price,asc");
    }

    @Test
    void returnsProductDetailWithChecklistSummary() throws Exception {
        when(productApplicationService.findPublicDetail(1001L))
                .thenReturn(new ProductDetailResponse(
                        1001L, 55L, null, null, "테스트 상품", null, null, "ON_SALE", null,
                        null, null, null, null));
        mockMvc.perform(get("/api/v1/products/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(1001))
                .andExpect(jsonPath("$.data.name").value("테스트 상품"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    void returnsGalaxyS24ChecklistTemplateMock() throws Exception {
        when(productCatalogService.checklistTemplate(101L))
                .thenReturn(new ChecklistTemplateResponse(501L, 101L, 1, List.of()));
        mockMvc.perform(get("/api/v1/device-models/101/checklist-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceModelId").value(101))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void returnsListingChecklistSnapshotWithCaptureGuide() throws Exception {
        when(productChecklistService.findAll(1001L, null, false))
                .thenReturn(List.of(new ProductChecklistItemResponse(
                        7003L,
                        "LAP-FTR-CAM",
                        "내장 카메라",
                        "카메라 앱을 실행해 영상 출력 상태를 확인하세요.",
                        "VIDEO",
                        false,
                        "PENDING",
                        null,
                        0)));

        mockMvc.perform(get("/api/v1/products/1001/checklist-items")
                        .header(
                                "Authorization",
                                "Bearer "
                                        + tokens.issueAccess(
                                                        55L,
                                                        "MEMBER",
                                                        Set.of("MEMBER", "SELLER"))
                                                .value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemCode").value("LAP-FTR-CAM"))
                .andExpect(jsonPath("$.data[0].name").value("내장 카메라"))
                .andExpect(jsonPath("$.data[0].guide")
                        .value("카메라 앱을 실행해 영상 출력 상태를 확인하세요."))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void requiresAuthenticationForMyProducts() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthenticationForMyProductDetail() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/products/1001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthenticationForMyFavorites() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthenticationForChecklistGeneration() throws Exception {
        mockMvc.perform(post("/api/v1/checklist-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceModelId\":201}"))
                .andExpect(status().isUnauthorized());
    }
}
