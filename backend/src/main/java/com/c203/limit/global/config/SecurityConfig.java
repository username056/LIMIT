package com.c203.limit.global.config;

import com.c203.limit.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter filter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        response.sendError(
                                                                HttpServletResponse
                                                                        .SC_UNAUTHORIZED))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        response.sendError(
                                                                HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/v1/auth/**",
                                                "/api/v1/health",
                                                "/api/v1/members",
                                                "/api/v1/admin/sessions",
                                                "/api/v1/inspection-agent/**",
                                                "/ws/rtc",
                                                "/actuator/health/**",
                                                "/ws",
                                                "/v3/api-docs/**",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                                        .permitAll()
                                        // /sellers/me는 본인 전용이라 아래 공개 규칙보다 먼저 잠근다.
                                        // {sellerId} 패턴이 'me'까지 삼켜 공개돼 버리는 것을 막기 위함이다.
                                        .requestMatchers(HttpMethod.GET, "/api/v1/sellers/me")
                                        .authenticated()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/products",
                                                "/api/v1/products/{productId}",
                                                // 검증 자료는 이 서비스에서 구매 판단의 근거다.
                                                // 로그인해야 볼 수 있으면 둘러보러 온 사람은
                                                // 무엇을 믿고 살지 판단할 수가 없다.
                                                "/api/v1/products/{productId}/checklist-items",
                                                "/api/v1/products/{productId}/checklist-items/{checklistItemId}/evidence",
                                                "/api/v1/sellers/{sellerId}",
                                                "/api/v1/device-categories",
                                                "/api/v1/device-models/**",
                                                "/api/v1/inspections/products/{productId}/diagnosis-summary")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasAnyRole("OPERATOR", "SUPER_ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
