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
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.domain.member.entity.Member;
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
    @MockitoBean ListingChatReader listingChatReader;
    @MockitoBean ListingRepository listingRepository;
    @MockitoBean ListingStatusHistoryRepository listingStatusHistoryRepository;
    @MockitoBean EvidenceRepository evidenceRepository;
    @MockitoBean OcrResultRepository ocrResultRepository;
    @MockitoBean ListingOwnerReader listingOwnerReader;
    @MockitoBean DxdiagResultRepository dxdiagResultRepository;
    @MockitoBean BatteryReportResultRepository batteryReportResultRepository;

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
}
