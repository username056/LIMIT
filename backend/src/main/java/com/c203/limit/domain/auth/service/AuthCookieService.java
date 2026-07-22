package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {
    private static final String REFRESH_PATH = "/api/v1/auth";
    private static final String SOCIAL_SIGNUP_PATH = "/api/v1/auth/social-signups";
    private static final String OAUTH_STATE_PATH = "/api/v1";
    private final AuthCookieProperties properties;
    private final Duration refreshTtl;

    public AuthCookieService(
            AuthCookieProperties properties,
            @Value("${limit.security.jwt.refresh-ttl:P14D}") Duration refreshTtl) {
        this.properties = properties;
        this.refreshTtl = refreshTtl;
    }

    public String refresh(String token) {
        return cookie(properties.getRefreshName(), token, REFRESH_PATH, refreshTtl).toString();
    }

    public String clearRefresh() {
        return cookie(properties.getRefreshName(), "", REFRESH_PATH, Duration.ZERO).toString();
    }

    public String socialSignup(String token) {
        return cookie(
                        properties.getSocialSignupName(),
                        token,
                        SOCIAL_SIGNUP_PATH,
                        properties.getSocialSignupTtl())
                .toString();
    }

    public String clearSocialSignup() {
        return cookie(properties.getSocialSignupName(), "", SOCIAL_SIGNUP_PATH, Duration.ZERO)
                .toString();
    }

    public String oauthState(String state) {
        return cookie(
                        properties.getOauthStateName(),
                        state,
                        OAUTH_STATE_PATH,
                        properties.getOauthStateTtl())
                .toString();
    }

    public String clearOauthState() {
        return cookie(properties.getOauthStateName(), "", OAUTH_STATE_PATH, Duration.ZERO)
                .toString();
    }

    public String refreshToken(HttpServletRequest request) {
        return value(request, properties.getRefreshName());
    }

    public String socialSignupToken(HttpServletRequest request) {
        return value(request, properties.getSocialSignupName());
    }

    public String oauthStateToken(HttpServletRequest request) {
        return value(request, properties.getOauthStateName());
    }

    private ResponseCookie cookie(String name, String value, String path, Duration ttl) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(path)
                .maxAge(ttl)
                .build();
    }

    private String value(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
