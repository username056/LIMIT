package com.c203.limit.domain.auth.controller;

import com.c203.limit.domain.admin.service.AdminService;
import com.c203.limit.domain.auth.dto.request.CompleteSocialSignupRequest;
import com.c203.limit.domain.auth.dto.request.LoginRequest;
import com.c203.limit.domain.auth.dto.request.SignupRequest;
import com.c203.limit.domain.auth.service.AuthCookieService;
import com.c203.limit.domain.auth.service.AuthService;
import com.c203.limit.domain.auth.service.EmailVerificationService;
import com.c203.limit.domain.auth.service.OAuthAuthorizationService;
import com.c203.limit.domain.auth.service.SessionResult;
import com.c203.limit.domain.auth.service.SocialAccountLoginService;
import com.c203.limit.domain.auth.service.SocialAuthService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import com.c203.limit.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {
    private final AuthService authService;
    private final AdminService adminService;
    private final SocialAuthService socialAuthService;
    private final OAuthAuthorizationService authorizationService;
    private final EmailVerificationService emailVerificationService;
    private final AuthCookieService authCookieService;
    private final CurrentUser currentUser;
    private final JwtTokenProvider tokenProvider;
    private final HttpServletRequest servletRequest;
    private final ObjectMapper objectMapper;

    public AuthController(
            AuthService authService,
            AdminService adminService,
            SocialAuthService socialAuthService,
            OAuthAuthorizationService authorizationService,
            EmailVerificationService emailVerificationService,
            AuthCookieService authCookieService,
            CurrentUser currentUser,
            JwtTokenProvider tokenProvider,
            HttpServletRequest servletRequest,
            ObjectMapper objectMapper) {
        this.authService = authService;
        this.adminService = adminService;
        this.socialAuthService = socialAuthService;
        this.authorizationService = authorizationService;
        this.emailVerificationService = emailVerificationService;
        this.authCookieService = authCookieService;
        this.currentUser = currentUser;
        this.tokenProvider = tokenProvider;
        this.servletRequest = servletRequest;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Void> auth01(String email) {
        return response(ResponseEntity.ok(ApiResponse.ok(authService.emailAvailability(email))));
    }

    @Override
    public ResponseEntity<Void> auth02(String nickname) {
        return response(
                ResponseEntity.ok(ApiResponse.ok(authService.nicknameAvailability(nickname))));
    }

    @Override
    public ResponseEntity<Void> auth03(Object body) {
        JsonNode json = json(body);
        var request =
                new SignupRequest(
                        text(json, "email"),
                        text(json, "password"),
                        text(json, "nickname"),
                        optional(json, "phone"),
                        bool(json, "serviceTermsAccepted"),
                        bool(json, "privacyTermsAccepted"),
                        bool(json, "ageRequirementAccepted"),
                        bool(json, "marketingAccepted"));
        return response(
                ResponseEntity.status(201).body(ApiResponse.ok(authService.signup(request))));
    }

    @Override
    public ResponseEntity<Void> auth04(Object body) {
        JsonNode json = json(body);
        SessionResult<?> result =
                authService.login(new LoginRequest(text(json, "email"), text(json, "password")));
        return sessionResponse(result);
    }

    @Override
    public ResponseEntity<Void> auth05() {
        String refreshToken = requiredRefreshToken();
        JwtTokenProvider.TokenClaims claims = tokenProvider.parse(refreshToken, "refresh");
        SessionResult<?> result =
                "ADMIN".equals(claims.accountType())
                        ? adminService.refresh(refreshToken)
                        : authService.refresh(refreshToken);
        return sessionResponse(result);
    }

    @Override
    public ResponseEntity<Void> auth06() {
        String refreshToken = authCookieService.refreshToken(servletRequest);
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefresh())
                .build();
    }

    @Override
    public ResponseEntity<Void> auth07(String provider, Object body) {
        JsonNode json = json(body);
        SocialAccountLoginService.SocialLoginResult result =
                socialAuthService.login(
                        provider,
                        text(json, "authorizationCode"),
                        text(json, "redirectUri"),
                        text(json, "state"),
                        authCookieService.oauthStateToken(servletRequest));
        ResponseEntity.BodyBuilder response =
                ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, authCookieService.clearOauthState());
        if (result.refreshToken() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE, authCookieService.refresh(result.refreshToken()));
        }
        if (result.signupToken() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE, authCookieService.socialSignup(result.signupToken()));
        }
        return response(response.body(ApiResponse.ok(result.response())));
    }

    @Override
    public ResponseEntity<Void> auth08() {
        return response(
                ResponseEntity.ok(
                        ApiResponse.ok(socialAuthService.accounts(currentUser.memberId()))));
    }

    @Override
    public ResponseEntity<Void> auth09(Long socialAccountId) {
        socialAuthService.unlink(currentUser.memberId(), socialAccountId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> auth10(Object body) {
        emailVerificationService.request(text(json(body), "email"));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> auth11(Object body) {
        return response(
                ResponseEntity.ok(
                        ApiResponse.ok(
                                emailVerificationService.verify(text(json(body), "token")))));
    }

    @Override
    public ResponseEntity<Void> auth12(String provider, Object body) {
        OAuthAuthorizationService.AuthorizationAttempt attempt =
                authorizationService.begin(provider, text(json(body), "redirectUri"));
        return response(
                ResponseEntity.ok()
                        .header(
                                HttpHeaders.SET_COOKIE,
                                authCookieService.oauthState(attempt.state()))
                        .body(ApiResponse.ok(attempt.response())));
    }

    @Override
    public ResponseEntity<Void> auth13(Object body) {
        JsonNode json = json(body);
        var request =
                new CompleteSocialSignupRequest(
                        text(json, "nickname"),
                        optional(json, "phone"),
                        bool(json, "serviceTermsAccepted"),
                        bool(json, "privacyTermsAccepted"),
                        bool(json, "ageRequirementAccepted"),
                        bool(json, "marketingAccepted"));
        SocialAccountLoginService.SocialLoginResult result =
                socialAuthService.completeSignup(
                        authCookieService.socialSignupToken(servletRequest), request);
        return response(
                ResponseEntity.ok()
                        .header(
                                HttpHeaders.SET_COOKIE,
                                authCookieService.clearSocialSignup(),
                                authCookieService.refresh(result.refreshToken()))
                        .body(ApiResponse.ok(result.response())));
    }

    @Override
    public ResponseEntity<Void> auth14(String provider, Object body) {
        OAuthAuthorizationService.AuthorizationAttempt attempt =
                authorizationService.beginLink(
                        provider, text(json(body), "redirectUri"), currentUser.memberId());
        return response(
                ResponseEntity.ok()
                        .header(
                                HttpHeaders.SET_COOKIE,
                                authCookieService.oauthState(attempt.state()))
                        .body(ApiResponse.ok(attempt.response())));
    }

    @Override
    public ResponseEntity<Void> auth15(String provider, Object body) {
        JsonNode json = json(body);
        Object account =
                socialAuthService.link(
                        currentUser.memberId(),
                        provider,
                        text(json, "authorizationCode"),
                        text(json, "redirectUri"),
                        text(json, "state"),
                        authCookieService.oauthStateToken(servletRequest));
        return response(
                ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, authCookieService.clearOauthState())
                        .body(ApiResponse.ok(account)));
    }

    private ResponseEntity<Void> sessionResponse(SessionResult<?> result) {
        return response(
                ResponseEntity.ok()
                        .header(
                                HttpHeaders.SET_COOKIE,
                                authCookieService.refresh(result.refreshToken()))
                        .body(ApiResponse.ok(result.body())));
    }

    private String requiredRefreshToken() {
        String token = authCookieService.refreshToken(servletRequest);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return token;
    }

    private JsonNode json(Object body) {
        if (body == null) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return objectMapper.valueToTree(body);
    }

    private String text(JsonNode json, String field) {
        String value = optional(json, field);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return value;
    }

    private String optional(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean bool(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Void> response(ResponseEntity<?> source) {
        return (ResponseEntity) source;
    }
}
