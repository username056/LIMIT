package com.c203.limit.domain.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductApplicationService.ProductPage;
import com.c203.limit.domain.product.service.ProductCatalogService;

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
    ListingStatusHistoryRepository listingStatusHistoryRepository;

    @MockitoBean
    WishlistRepository wishlistRepository;

    @MockitoBean
    ProductApplicationService productApplicationService;

    @MockitoBean
    ProductCatalogService productCatalogService;

    @MockitoBean
    com.c203.limit.domain.seller.repository.SellerRepository sellerRepository;

    @Autowired
    MockMvc mockMvc;

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
}
