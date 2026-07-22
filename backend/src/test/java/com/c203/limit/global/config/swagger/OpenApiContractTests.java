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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.member.repository.MemberRepository;

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

    @MockitoBean
    ChatRoomRepository chatRoomRepository;

    @MockitoBean
    ChatRoomParticipantRepository chatRoomParticipantRepository;

    @MockitoBean
    ListingChatReader listingChatReader;

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
                .andExpect(jsonPath("$.paths['/api/v1/members'].post.operationId").value("auth03"))
                .andExpect(jsonPath("$.paths['/api/v1/members/me'].get.operationId").value("member01"))
                .andExpect(jsonPath("$.components.schemas.SignupRequest").exists())
                .andExpect(jsonPath("$.components.schemas.MemberProfileResponse").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products']").doesNotExist());
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

        mockMvc.perform(get("/v3/api-docs/03-chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/listings/{listingId}/chat-rooms'].post.operationId")
                        .value("chatBe01"))
                .andExpect(jsonPath("$.paths['/api/v1/listings/{listingId}/chat-rooms'].post.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms'].get.operationId")
                        .value("chatBe07"))
                .andExpect(jsonPath("$.paths['/api/v1/chat-rooms'].get.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls.length()").value(3))
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
