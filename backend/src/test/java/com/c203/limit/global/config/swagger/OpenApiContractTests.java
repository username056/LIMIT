package com.c203.limit.global.config.swagger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.seller.repository.SellerApplicationRepository;
import com.c203.limit.domain.seller.repository.SellerApplicationDocumentRepository;
import com.c203.limit.domain.seller.repository.SellerRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                .andExpect(jsonPath("$.components.schemas.ApiResponse").exists())
                .andExpect(jsonPath("$.components.schemas.OrderDetailResponse").exists())
                .andExpect(jsonPath("$.components.schemas.QueueStatusResponse").exists());
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
