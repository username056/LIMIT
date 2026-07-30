package com.c203.limit.domain.seller.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c203.limit.domain.seller.dto.response.PublicSellerProfileResponse;
import com.c203.limit.domain.seller.service.SellerService;
import com.c203.limit.global.config.JacksonConfig;
import com.c203.limit.global.config.SecurityConfig;
import com.c203.limit.global.security.CurrentUser;
import com.c203.limit.global.security.JwtAuthenticationFilter;
import com.c203.limit.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 판매자 API의 공개 범위를 실제 Security 필터 체인으로 검증한다.
 *
 * <p>{@code /api/v1/sellers/{sellerId}}를 공개하면 경로 패턴이 {@code me}까지 삼켜서
 * {@code /api/v1/sellers/me}(본인 전용)가 인증 없이 열릴 수 있다. 그 사고를 막는 규칙 순서가
 * 유지되는지 확인하는 것이 이 테스트의 목적이다.
 */
@WebMvcTest(controllers = SellerController.class)
@EnableWebSecurity
@Import({
    JacksonConfig.class,
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    CurrentUser.class,
    JwtTokenProvider.class
})
class SellerSecurityControllerTests {

    @TestConfiguration
    static class DisableTopLevelJwtFilterRegistrationConfig {
        @Bean
        FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
                JwtAuthenticationFilter filter) {
            FilterRegistrationBean<JwtAuthenticationFilter> registration =
                    new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }

    @MockitoBean SellerService sellerService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired MockMvc mockMvc;

    @Test
    void publicSellerProfileIsReadableWithoutToken() throws Exception {
        when(sellerService.publicProfile(anyLong()))
                .thenReturn(new PublicSellerProfileResponse(
                        55L, "리미트판매자", "INDIVIDUAL", null, 3L));

        mockMvc.perform(get("/api/v1/sellers/{sellerId}", 55L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("리미트판매자"))
                .andExpect(jsonPath("$.data.onSaleCount").value(3));
    }

    // 공개 패턴이 'me'를 삼켜 본인 프로필이 열리는 것을 막는다.
    @Test
    void mySellerProfileStillRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/sellers/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void sellerRegistrationStillRequiresToken() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/sellers"))
                .andExpect(status().isUnauthorized());
    }
}
