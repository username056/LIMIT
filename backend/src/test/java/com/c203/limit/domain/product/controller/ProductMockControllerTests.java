package com.c203.limit.domain.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;

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
    ListingChatReader listingChatReader;

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
    DxdiagResultRepository dxdiagResultRepository;

    @MockitoBean
    BatteryReportResultRepository batteryReportResultRepository;

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsPublicProductListMock() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(1001))
                .andExpect(jsonPath("$.data[0].modelName").value("Galaxy S24"))
                .andExpect(jsonPath("$.data[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void returnsProductDetailWithChecklistSummary() throws Exception {
        mockMvc.perform(get("/api/v1/products/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(1001))
                .andExpect(jsonPath("$.data.device.model").value("Galaxy S24"))
                .andExpect(jsonPath("$.data.checklistSummary.required").value(18))
                .andExpect(jsonPath("$.data.checklistSummary.completed").value(18));
    }

    @Test
    void returnsGalaxyS24ChecklistTemplateMock() throws Exception {
        mockMvc.perform(get("/api/v1/device-models/101/checklist-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceModelId").value(101))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items[0].itemCode").value("SP-EXT-001"));
    }

    @Test
    void requiresAuthenticationForMyProducts() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/products"))
                .andExpect(status().isUnauthorized());
    }
}
