package com.c203.limit.global.config.swagger;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final List<Class<?>> SCHEMA_TYPES =
            List.of(
                    com.c203.limit.domain.auth.dto.request.LoginRequest.class,
                    com.c203.limit.domain.auth.dto.request.CompleteSocialSignupRequest.class,
                    com.c203.limit.domain.auth.dto.request.OAuthAuthorizationRequest.class,
                    com.c203.limit.domain.auth.dto.request.LogoutRequest.class,
                    com.c203.limit.domain.auth.dto.request.EmailVerificationRequest.class,
                    com.c203.limit.domain.auth.dto.request.SignupRequest.class,
                    com.c203.limit.domain.auth.dto.request.SocialLoginRequest.class,
                    com.c203.limit.domain.auth.dto.request.TokenRefreshRequest.class,
                    com.c203.limit.domain.auth.dto.request.VerifyEmailRequest.class,
                    com.c203.limit.domain.auth.dto.response.EmailAvailabilityResponse.class,
                    com.c203.limit.domain.auth.dto.response.OAuthAuthorizationResponse.class,
                    com.c203.limit.domain.auth.dto.response.EmailVerificationResponse.class,
                    com.c203.limit.domain.auth.dto.response.LoginResponse.class,
                    com.c203.limit.domain.auth.dto.response.NicknameAvailabilityResponse.class,
                    com.c203.limit.domain.auth.dto.response.SignupResponse.class,
                    com.c203.limit.domain.auth.dto.response.SocialAccountResponse.class,
                    com.c203.limit.domain.auth.dto.response.SocialLoginResponse.class,
                    com.c203.limit.domain.auth.dto.response.SocialSignupPreviewResponse.class,
                    com.c203.limit.domain.auth.dto.response.TokenResponse.class,
                    com.c203.limit.domain.member.dto.request.ChangePasswordRequest.class,
                    com.c203.limit.domain.member.dto.request.UpdateMemberRequest.class,
                    com.c203.limit.domain.member.dto.response.MemberProfileResponse.class,
                    com.c203.limit.domain.member.dto.response.MemberSummaryResponse.class,
                    com.c203.limit.domain.member.dto.response.UpdateMemberResponse.class,
                    com.c203.limit.domain.admin.dto.request.AdminLoginRequest.class,
                    com.c203.limit.domain.admin.dto.request.CreateAdminAccountRequest.class,
                    com.c203.limit.domain.admin.dto.request.CreateMemberRestrictionRequest.class,
                    com.c203.limit.domain.admin.dto.request.ReleaseMemberRestrictionRequest.class,
                    com.c203.limit.domain.admin.dto.request.UpdateAdminAccountAccessRequest.class,
                    com.c203.limit.domain.admin.dto.response.AdminAccountResponse.class,
                    com.c203.limit.global.response.ApiResponse.class,
                    com.c203.limit.global.response.PageResponse.class,
                    com.c203.limit.global.response.ApiErrorResponse.class);

    @Bean
    OpenAPI limitOpenApi() {
        Components components =
                new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSecuritySchemes(
                                "internalApiKey",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Internal-Api-Key"));

        for (Class<?> schemaType : SCHEMA_TYPES) {
            Map<String, Schema> schemas = ModelConverters.getInstance().readAll(schemaType);
            schemas.forEach(components::addSchemas);
        }

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Limit API")
                                .version("v1")
                                .description("Limit API v1 문서입니다."))
                .components(components);
    }
}
