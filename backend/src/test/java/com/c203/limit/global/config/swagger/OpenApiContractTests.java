package com.c203.limit.global.config.swagger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.seller.repository.SellerApplicationRepository;
import com.c203.limit.domain.seller.repository.SellerApplicationDocumentRepository;
import com.c203.limit.domain.seller.repository.SellerRepository;
import com.c203.limit.domain.admin.repository.*;
import com.c203.limit.domain.inquiry.repository.*;
import com.c203.limit.domain.withdrawal.repository.WithdrawalRequestRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

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
class OpenApiContractTests {

    @MockitoBean
    MemberRepository memberRepository;
    @MockitoBean
    SocialAccountRepository socialAccountRepository;
    @MockitoBean SellerApplicationRepository sellerApplicationRepository;
    @MockitoBean SellerApplicationDocumentRepository sellerApplicationDocumentRepository;
    @MockitoBean SellerRepository sellerRepository;
    @MockitoBean AdminAccountRepository adminAccountRepository;
    @MockitoBean MemberRestrictionRepository memberRestrictionRepository;
    @MockitoBean AdminActionLogRepository adminActionLogRepository;
    @MockitoBean MemberRoleAssignmentRepository memberRoleAssignmentRepository;
    @MockitoBean InquiryRepository inquiryRepository;
    @MockitoBean InquiryAnswerRepository inquiryAnswerRepository;
    @MockitoBean WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesGeneratedOperationsAndSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.paths['/api/v1/members'].post.operationId").value("auth03"))
                .andExpect(jsonPath("$.paths['/api/v1/members'].post.tags[0]").value("01. 인증"))
                .andExpect(jsonPath("$.paths['/api/v1/products/{productId}'].get.operationId").value("product5"))
                .andExpect(jsonPath("$.paths['/api/v1/products/{productId}'].get.tags[0]").value("04. 상품"))
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}/cancellations'].post.operationId").value("order03"))
                .andExpect(jsonPath("$.paths['/api/v1/internal/stock-reservations/{reservationId}/confirmations'].post.operationId").value("stock02"))
                .andExpect(jsonPath("$.paths['/internal/v1/stock-reservations/{reservationId}/confirm']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SignupRequest").exists())
                .andExpect(jsonPath("$.components.schemas.OrderDetailResponse").exists())
                .andExpect(jsonPath("$.components.schemas.QueueStatusResponse").exists())
                .andExpect(jsonPath("$.paths['/api/v1/hello']").doesNotExist())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    @Test
    void exposesDomainGroupsAndMethodOrderConfiguration() throws Exception {
        mockMvc.perform(get("/v3/api-docs/04-product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/products']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/hello']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/08-payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/payments']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls.length()").value(17))
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

    @Test
    void publicAvailabilityEndpointReturnsCommonResponse() throws Exception {
        mockMvc.perform(get("/api/v1/auth/email-availability").param("email", "user@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
