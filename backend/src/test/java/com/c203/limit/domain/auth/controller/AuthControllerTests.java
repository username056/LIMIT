package com.c203.limit.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.service.AdminService;
import com.c203.limit.domain.auth.dto.request.CompleteSocialSignupRequest;
import com.c203.limit.domain.auth.dto.request.LoginRequest;
import com.c203.limit.domain.auth.dto.request.SignupRequest;
import com.c203.limit.domain.auth.dto.response.EmailAvailabilityResponse;
import com.c203.limit.domain.auth.dto.response.EmailVerificationResponse;
import com.c203.limit.domain.auth.dto.response.LoginResponse;
import com.c203.limit.domain.auth.dto.response.NicknameAvailabilityResponse;
import com.c203.limit.domain.auth.dto.response.OAuthAuthorizationResponse;
import com.c203.limit.domain.auth.dto.response.SignupResponse;
import com.c203.limit.domain.auth.dto.response.SocialAccountResponse;
import com.c203.limit.domain.auth.dto.response.SocialLoginResponse;
import com.c203.limit.domain.auth.dto.response.TokenResponse;
import com.c203.limit.domain.auth.service.AuthCookieService;
import com.c203.limit.domain.auth.service.AuthService;
import com.c203.limit.domain.auth.service.EmailVerificationService;
import com.c203.limit.domain.auth.service.OAuthAuthorizationService;
import com.c203.limit.domain.auth.service.PasswordResetService;
import com.c203.limit.domain.auth.service.SessionResult;
import com.c203.limit.domain.auth.service.SocialAccountLoginService;
import com.c203.limit.domain.auth.service.SocialAuthService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import com.c203.limit.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {
    private static final Long MEMBER_ID = 20L;

    @Mock AuthService authService;
    @Mock AdminService adminService;
    @Mock SocialAuthService socialAuthService;
    @Mock OAuthAuthorizationService authorizationService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock PasswordResetService passwordResetService;
    @Mock AuthCookieService authCookieService;
    @Mock CurrentUser currentUser;
    @Mock JwtTokenProvider tokenProvider;
    @Mock HttpServletRequest servletRequest;

    AuthController controller;

    @BeforeEach
    void setUp() {
        controller =
                new AuthController(
                        authService,
                        adminService,
                        socialAuthService,
                        authorizationService,
                        emailVerificationService,
                        passwordResetService,
                        authCookieService,
                        currentUser,
                        tokenProvider,
                        servletRequest,
                        new ObjectMapper());
    }

    @Test
    void returnsEmailAvailability() {
        EmailAvailabilityResponse availability =
                new EmailAvailabilityResponse("user@example.com", true);
        when(authService.emailAvailability("user@example.com")).thenReturn(availability);

        ResponseEntity<Void> response = controller.auth01("user@example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(data(response)).isSameAs(availability);
    }

    @Test
    void returnsNicknameAvailability() {
        NicknameAvailabilityResponse availability =
                new NicknameAvailabilityResponse("openrunner", false);
        when(authService.nicknameAvailability("openrunner")).thenReturn(availability);

        assertThat(data(controller.auth02("openrunner"))).isSameAs(availability);
    }

    @Test
    void returns201AndMapsEveryTermsFlagOnSignup() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "user@example.com");
        body.put("password", "Password123!");
        body.put("nickname", "openrunner");
        body.put("phone", "01012345678");
        body.put("serviceTermsAccepted", true);
        body.put("privacyTermsAccepted", true);
        body.put("ageRequirementAccepted", true);
        body.put("marketingAccepted", false);
        SignupResponse signup =
                new SignupResponse(
                        MEMBER_ID,
                        "user@example.com",
                        "openrunner",
                        "ACTIVE",
                        Set.of("MEMBER"),
                        LocalDateTime.of(2026, 7, 16, 11, 0));
        when(authService.signup(any(SignupRequest.class))).thenReturn(signup);

        ResponseEntity<Void> response = controller.auth03(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(data(response)).isSameAs(signup);
        ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
        verify(authService).signup(captor.capture());
        SignupRequest captured = captor.getValue();
        assertThat(captured.getEmail()).isEqualTo("user@example.com");
        assertThat(captured.getPassword()).isEqualTo("Password123!");
        assertThat(captured.getNickname()).isEqualTo("openrunner");
        assertThat(captured.getPhone()).isEqualTo("01012345678");
        assertThat(captured.isServiceTermsAccepted()).isTrue();
        assertThat(captured.isPrivacyTermsAccepted()).isTrue();
        assertThat(captured.isAgeRequirementAccepted()).isTrue();
        assertThat(captured.isMarketingAccepted()).isFalse();
    }

    @Test
    void treatsMissingPhoneAsNullAndNonBooleanTermsAsNotAccepted() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "user@example.com");
        body.put("password", "Password123!");
        body.put("nickname", "openrunner");
        body.put("phone", null);
        body.put("serviceTermsAccepted", "true");
        when(authService.signup(any(SignupRequest.class))).thenReturn(null);

        controller.auth03(body);

        ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
        verify(authService).signup(captor.capture());
        assertThat(captor.getValue().getPhone()).isNull();
        assertThat(captor.getValue().isServiceTermsAccepted()).isFalse();
        assertThat(captor.getValue().isMarketingAccepted()).isFalse();
    }

    @Test
    void rejectsSignupWithoutRequiredEmail() {
        Map<String, Object> body = new HashMap<>();
        body.put("password", "Password123!");
        body.put("nickname", "openrunner");

        assertThatThrownBy(() -> controller.auth03(body))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsBlankRequiredField() {
        assertThatThrownBy(() -> controller.auth10(Map.of("email", "   ")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void rejectsNullRequestBody() {
        assertThatThrownBy(() -> controller.auth04(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(authService);
    }

    @Test
    void setsRefreshCookieOnMemberLogin() {
        LoginResponse login = new LoginResponse("access-value", "Bearer", 1800L, null);
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new SessionResult<>(login, "refresh-value"));
        when(authCookieService.refresh("refresh-value")).thenReturn("refresh=refresh-value");

        ResponseEntity<Void> response =
                controller.auth04(Map.of("email", "user@example.com", "password", "Password123!"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("refresh=refresh-value");
        assertThat(data(response)).isSameAs(login);
        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getPassword()).isEqualTo("Password123!");
    }

    @Test
    void refreshesMemberSessionWhenAccountTypeIsNotAdmin() {
        TokenResponse token = new TokenResponse("new-access", "Bearer", 1800L, null);
        when(authCookieService.refreshToken(servletRequest)).thenReturn("refresh-value");
        when(tokenProvider.parse("refresh-value", "refresh"))
                .thenReturn(
                        new JwtTokenProvider.TokenClaims(
                                "token-id", MEMBER_ID, "MEMBER", Set.of("MEMBER")));
        when(authService.refresh("refresh-value"))
                .thenReturn(new SessionResult<>(token, "rotated-value"));
        when(authCookieService.refresh("rotated-value")).thenReturn("refresh=rotated-value");

        ResponseEntity<Void> response = controller.auth05();

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("refresh=rotated-value");
        assertThat(data(response)).isSameAs(token);
        verifyNoInteractions(adminService);
    }

    @Test
    void refreshesAdminSessionWhenAccountTypeIsAdmin() {
        Map<String, Object> body = Map.of("accessToken", "new-access");
        when(authCookieService.refreshToken(servletRequest)).thenReturn("refresh-value");
        when(tokenProvider.parse("refresh-value", "refresh"))
                .thenReturn(
                        new JwtTokenProvider.TokenClaims(
                                "token-id", 1L, "ADMIN", Set.of("SUPER_ADMIN")));
        when(adminService.refresh("refresh-value"))
                .thenReturn(new SessionResult<>(body, "rotated-value"));
        when(authCookieService.refresh("rotated-value")).thenReturn("refresh=rotated-value");

        assertThat(data(controller.auth05())).isSameAs(body);
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsRefreshWhenCookieIsMissing() {
        when(authCookieService.refreshToken(servletRequest)).thenReturn(null);

        assertThatThrownBy(() -> controller.auth05())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_TOKEN));
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void rejectsRefreshWhenCookieIsBlank() {
        when(authCookieService.refreshToken(servletRequest)).thenReturn("   ");

        assertThatThrownBy(() -> controller.auth05())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_TOKEN));
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void logsOutAndClearsCookieWhenRefreshTokenPresent() {
        when(authCookieService.refreshToken(servletRequest)).thenReturn("refresh-value");
        when(authCookieService.clearRefresh()).thenReturn("refresh=; Max-Age=0");

        ResponseEntity<Void> response = controller.auth06();

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("refresh=; Max-Age=0");
        verify(authService).logout("refresh-value");
    }

    @Test
    void clearsCookieWithoutLogoutCallWhenRefreshTokenMissing() {
        when(authCookieService.refreshToken(servletRequest)).thenReturn("");
        when(authCookieService.clearRefresh()).thenReturn("refresh=; Max-Age=0");

        ResponseEntity<Void> response = controller.auth06();

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verifyNoInteractions(authService);
    }

    @Test
    void setsRefreshCookieWhenSocialLoginReturnsExistingMember() {
        SocialLoginResponse socialResponse =
                new SocialLoginResponse("AUTHENTICATED", "access-value", "Bearer", 1800L, null, null);
        when(authCookieService.oauthStateToken(servletRequest)).thenReturn("state-cookie");
        when(socialAuthService.login("google", "auth-code", "https://limit/callback", "state", "state-cookie"))
                .thenReturn(
                        new SocialAccountLoginService.SocialLoginResult(
                                socialResponse, "refresh-value", null));
        when(authCookieService.clearOauthState()).thenReturn("oauth_state=; Max-Age=0");
        when(authCookieService.refresh("refresh-value")).thenReturn("refresh=refresh-value");

        ResponseEntity<Void> response =
                controller.auth07(
                        "google",
                        Map.of(
                                "authorizationCode", "auth-code",
                                "redirectUri", "https://limit/callback",
                                "state", "state"));

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("oauth_state=; Max-Age=0", "refresh=refresh-value");
        assertThat(data(response)).isSameAs(socialResponse);
    }

    @Test
    void setsSignupCookieWhenSocialLoginNeedsSignup() {
        SocialLoginResponse socialResponse =
                new SocialLoginResponse("SIGNUP_REQUIRED", null, null, null, null, null);
        when(authCookieService.oauthStateToken(servletRequest)).thenReturn("state-cookie");
        when(socialAuthService.login("kakao", "auth-code", "https://limit/callback", "state", "state-cookie"))
                .thenReturn(
                        new SocialAccountLoginService.SocialLoginResult(
                                socialResponse, null, "signup-token"));
        when(authCookieService.clearOauthState()).thenReturn("oauth_state=; Max-Age=0");
        when(authCookieService.socialSignup("signup-token")).thenReturn("signup=signup-token");

        ResponseEntity<Void> response =
                controller.auth07(
                        "kakao",
                        Map.of(
                                "authorizationCode", "auth-code",
                                "redirectUri", "https://limit/callback",
                                "state", "state"));

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("oauth_state=; Max-Age=0", "signup=signup-token");
    }

    @Test
    void returnsLinkedSocialAccountsOfCurrentMember() {
        List<SocialAccountResponse> accounts =
                List.of(
                        new SocialAccountResponse(
                                11L, "GOOGLE", "u***@gmail.com", LocalDateTime.of(2026, 7, 1, 10, 0)));
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(socialAuthService.accounts(MEMBER_ID)).thenReturn(accounts);

        assertThat(data(controller.auth08())).isSameAs(accounts);
    }

    @Test
    void unlinksSocialAccountOfCurrentMember() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);

        ResponseEntity<Void> response = controller.auth09(11L);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(socialAuthService).unlink(MEMBER_ID, 11L);
    }

    @Test
    void acceptsEmailVerificationRequest() {
        ResponseEntity<Void> response = controller.auth10(Map.of("email", "user@example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(emailVerificationService).request("user@example.com");
    }

    @Test
    void returnsEmailVerificationResult() {
        EmailVerificationResponse verified =
                new EmailVerificationResponse(true, LocalDateTime.of(2026, 7, 16, 11, 0));
        when(emailVerificationService.verify("verify-token")).thenReturn(verified);

        assertThat(data(controller.auth11(Map.of("token", "verify-token")))).isSameAs(verified);
    }

    @Test
    void setsOauthStateCookieWhenAuthorizationBegins() {
        OAuthAuthorizationResponse authorization =
                new OAuthAuthorizationResponse("https://accounts.google.com/o/oauth2/auth");
        when(authorizationService.begin("google", "https://limit/callback"))
                .thenReturn(
                        new OAuthAuthorizationService.AuthorizationAttempt(
                                authorization, "state-value"));
        when(authCookieService.oauthState("state-value")).thenReturn("oauth_state=state-value");

        ResponseEntity<Void> response =
                controller.auth12("google", Map.of("redirectUri", "https://limit/callback"));

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("oauth_state=state-value");
        assertThat(data(response)).isSameAs(authorization);
    }

    @Test
    void clearsSignupCookieAndSetsRefreshCookieWhenSocialSignupCompletes() {
        SocialLoginResponse socialResponse =
                new SocialLoginResponse("AUTHENTICATED", "access-value", "Bearer", 1800L, null, null);
        when(authCookieService.socialSignupToken(servletRequest)).thenReturn("signup-token");
        when(socialAuthService.completeSignup(
                        org.mockito.ArgumentMatchers.eq("signup-token"),
                        any(CompleteSocialSignupRequest.class)))
                .thenReturn(
                        new SocialAccountLoginService.SocialLoginResult(
                                socialResponse, "refresh-value", null));
        when(authCookieService.clearSocialSignup()).thenReturn("signup=; Max-Age=0");
        when(authCookieService.refresh("refresh-value")).thenReturn("refresh=refresh-value");

        Map<String, Object> body = new HashMap<>();
        body.put("nickname", "openrunner");
        body.put("phone", null);
        body.put("serviceTermsAccepted", true);
        body.put("privacyTermsAccepted", true);
        body.put("ageRequirementAccepted", true);
        body.put("marketingAccepted", true);

        ResponseEntity<Void> response = controller.auth13(body);

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("signup=; Max-Age=0", "refresh=refresh-value");
        assertThat(data(response)).isSameAs(socialResponse);
        ArgumentCaptor<CompleteSocialSignupRequest> captor =
                ArgumentCaptor.forClass(CompleteSocialSignupRequest.class);
        verify(socialAuthService)
                .completeSignup(org.mockito.ArgumentMatchers.eq("signup-token"), captor.capture());
        assertThat(captor.getValue().nickname()).isEqualTo("openrunner");
        assertThat(captor.getValue().phone()).isNull();
        assertThat(captor.getValue().marketingAccepted()).isTrue();
    }

    @Test
    void setsOauthStateCookieWhenLinkAuthorizationBegins() {
        OAuthAuthorizationResponse authorization =
                new OAuthAuthorizationResponse("https://nid.naver.com/oauth2.0/authorize");
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(authorizationService.beginLink("naver", "https://limit/callback", MEMBER_ID))
                .thenReturn(
                        new OAuthAuthorizationService.AuthorizationAttempt(
                                authorization, "state-value"));
        when(authCookieService.oauthState("state-value")).thenReturn("oauth_state=state-value");

        ResponseEntity<Void> response =
                controller.auth14("naver", Map.of("redirectUri", "https://limit/callback"));

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("oauth_state=state-value");
        assertThat(data(response)).isSameAs(authorization);
    }

    @Test
    void clearsOauthStateCookieWhenSocialAccountIsLinked() {
        SocialAccountResponse account =
                new SocialAccountResponse(
                        11L, "NAVER", "u***@naver.com", LocalDateTime.of(2026, 7, 1, 10, 0));
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(authCookieService.oauthStateToken(servletRequest)).thenReturn("state-cookie");
        when(socialAuthService.link(
                        MEMBER_ID,
                        "naver",
                        "auth-code",
                        "https://limit/callback",
                        "state",
                        "state-cookie"))
                .thenReturn(account);
        when(authCookieService.clearOauthState()).thenReturn("oauth_state=; Max-Age=0");

        ResponseEntity<Void> response =
                controller.auth15(
                        "naver",
                        Map.of(
                                "authorizationCode", "auth-code",
                                "redirectUri", "https://limit/callback",
                                "state", "state"));

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("oauth_state=; Max-Age=0");
        assertThat(data(response)).isSameAs(account);
    }

    @Test
    void acceptsPasswordResetRequest() {
        ResponseEntity<Void> response = controller.auth16(Map.of("email", "user@example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(passwordResetService).request("user@example.com");
    }

    @Test
    void returns204WhenPasswordIsReset() {
        ResponseEntity<Void> response =
                controller.auth17(Map.of("token", "reset-token", "newPassword", "NewPassword123!"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(passwordResetService).reset("reset-token", "NewPassword123!");
    }

    @Test
    void rejectsPasswordResetWithoutNewPassword() {
        assertThatThrownBy(() -> controller.auth17(Map.of("token", "reset-token")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(passwordResetService);
    }

    private Object data(ResponseEntity<?> response) {
        return ((ApiResponse<?>) response.getBody()).data();
    }
}
