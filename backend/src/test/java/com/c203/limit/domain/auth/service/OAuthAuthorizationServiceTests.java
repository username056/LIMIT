package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.auth.config.AuthCookieProperties;
import com.c203.limit.domain.auth.config.SocialOAuthProperties;
import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuthAuthorizationServiceTests {
    private static final String NAVER_REDIRECT = "http://localhost:5173/auth/callback/naver";
    private static final String NAVER_ALT_REDIRECT = "http://localhost:5173/auth/callback/naver2";
    private static final String GOOGLE_REDIRECT = "http://localhost:5173/auth/callback/google";
    private static final String KAKAO_REDIRECT = "http://localhost:5173/auth/callback/kakao";
    private OAuthAuthorizationService service;

    @BeforeEach
    void setUp() {
        SocialOAuthProperties properties = new SocialOAuthProperties();
        properties.getNaver().setEnabled(true);
        properties.getNaver().setClientId("naver-client");
        properties.getNaver().setRedirectUris(NAVER_REDIRECT + " , " + NAVER_ALT_REDIRECT);
        properties.getGoogle().setEnabled(true);
        properties.getGoogle().setClientId("google-client");
        properties.getGoogle().setRedirectUris(GOOGLE_REDIRECT);
        properties.getKakao().setEnabled(true);
        properties.getKakao().setClientId("kakao-client");
        properties.getKakao().setRedirectUris(KAKAO_REDIRECT);
        service =
                new OAuthAuthorizationService(
                        properties,
                        new AuthCookieProperties(),
                        new InMemoryOAuthAttemptStore());
    }

    @Test
    void issuesAndConsumesOneTimeStateForLogin() {
        var attempt = service.begin("naver", "http://localhost:5173/auth/callback/naver");

        assertThat(attempt.response().authorizationUrl())
                .contains("nid.naver.com")
                .contains("client_id=naver-client")
                .contains("state=");
        service.consume(
                SocialProvider.NAVER,
                "http://localhost:5173/auth/callback/naver",
                attempt.state(),
                attempt.state());

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        SocialProvider.NAVER,
                                        "http://localhost:5173/auth/callback/naver",
                                        attempt.state(),
                                        attempt.state()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }

    @Test
    void bindsLinkStateToAuthenticatedMember() {
        var attempt =
                service.beginLink(
                        "naver", "http://localhost:5173/auth/callback/naver", 7L);

        assertThatThrownBy(
                        () ->
                                service.consumeLink(
                                        SocialProvider.NAVER,
                                        "http://localhost:5173/auth/callback/naver",
                                        attempt.state(),
                                        attempt.state(),
                                        8L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }

    @Test
    void rejectsMismatchedBrowserCookieBeforeConsumingState() {
        var attempt = service.begin("naver", "http://localhost:5173/auth/callback/naver");

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        SocialProvider.NAVER,
                                        "http://localhost:5173/auth/callback/naver",
                                        attempt.state(),
                                        "different"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }

    @Test
    void buildsGoogleAuthorizationUrlWithOpenIdScopeAndAccountPrompt() {
        var attempt = service.begin("google", GOOGLE_REDIRECT);

        assertThat(attempt.response().authorizationUrl())
                .contains("accounts.google.com/o/oauth2/v2/auth")
                .contains("client_id=google-client")
                .contains("response_type=code")
                .contains("scope=openid%20email%20profile")
                .contains("prompt=select_account");
    }

    @Test
    void buildsKakaoAuthorizationUrlWithoutScopeParameters() {
        var attempt = service.begin("kakao", KAKAO_REDIRECT);

        assertThat(attempt.response().authorizationUrl())
                .contains("kauth.kakao.com/oauth/authorize")
                .contains("client_id=kakao-client")
                .doesNotContain("scope=")
                .doesNotContain("prompt=");
    }

    @Test
    void acceptsProviderValueRegardlessOfLetterCase() {
        var attempt = service.begin("GoOgLe", GOOGLE_REDIRECT);

        assertThat(attempt.response().authorizationUrl()).contains("accounts.google.com");
    }

    @Test
    void rejectsUnknownOrMissingProviderValue() {
        assertThatThrownBy(() -> service.begin("apple", GOOGLE_REDIRECT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
        assertThatThrownBy(() -> service.begin(null, GOOGLE_REDIRECT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
    }

    @Test
    void rejectsDisabledProvider() {
        SocialOAuthProperties properties = new SocialOAuthProperties();
        properties.getGoogle().setEnabled(false);
        properties.getGoogle().setClientId("google-client");
        properties.getGoogle().setRedirectUris(GOOGLE_REDIRECT);
        OAuthAuthorizationService disabled =
                new OAuthAuthorizationService(
                        properties, new AuthCookieProperties(), new InMemoryOAuthAttemptStore());

        assertThatThrownBy(() -> disabled.begin("google", GOOGLE_REDIRECT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
    }

    @Test
    void rejectsEnabledProviderWithoutConfiguredClientId() {
        SocialOAuthProperties properties = new SocialOAuthProperties();
        properties.getGoogle().setEnabled(true);
        properties.getGoogle().setRedirectUris(GOOGLE_REDIRECT);
        OAuthAuthorizationService misconfigured =
                new OAuthAuthorizationService(
                        properties, new AuthCookieProperties(), new InMemoryOAuthAttemptStore());

        assertThatThrownBy(() -> misconfigured.begin("google", GOOGLE_REDIRECT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
    }

    @Test
    void rejectsRedirectUriOutsideAllowlist() {
        assertThatThrownBy(() -> service.begin("naver", "https://attacker.example/callback"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
        assertThatThrownBy(() -> service.begin("naver", "   "))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
    }

    @Test
    void rejectsProviderWithoutConfiguredRedirectAllowlist() {
        SocialOAuthProperties properties = new SocialOAuthProperties();
        properties.getKakao().setEnabled(true);
        properties.getKakao().setClientId("kakao-client");
        OAuthAuthorizationService misconfigured =
                new OAuthAuthorizationService(
                        properties, new AuthCookieProperties(), new InMemoryOAuthAttemptStore());

        assertThatThrownBy(() -> misconfigured.begin("kakao", KAKAO_REDIRECT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
    }

    @Test
    void acceptsAnyEntryOfCommaSeparatedRedirectAllowlist() {
        var attempt = service.begin("naver", NAVER_ALT_REDIRECT);

        assertThat(attempt.state()).isNotBlank();
        service.consume(SocialProvider.NAVER, NAVER_ALT_REDIRECT, attempt.state(), attempt.state());
    }

    @Test
    void rejectsCallbackWithoutStateOnEitherSide() {
        var attempt = service.begin("naver", NAVER_REDIRECT);

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        SocialProvider.NAVER, NAVER_REDIRECT, null, attempt.state()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        assertThatThrownBy(
                        () ->
                                service.consume(
                                        SocialProvider.NAVER, NAVER_REDIRECT, attempt.state(), " "))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }

    @Test
    void rejectsStateIssuedForAnotherProvider() {
        var attempt = service.begin("naver", NAVER_REDIRECT);

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        SocialProvider.GOOGLE,
                                        NAVER_REDIRECT,
                                        attempt.state(),
                                        attempt.state()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }

    @Test
    void rejectsStateReplayedWithAnotherAllowedRedirectUri() {
        var attempt = service.begin("naver", NAVER_REDIRECT);

        assertThatThrownBy(
                        () ->
                                service.consume(
                                        SocialProvider.NAVER,
                                        NAVER_ALT_REDIRECT,
                                        attempt.state(),
                                        attempt.state()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }

    @Test
    void rejectsLoginStateReusedOnLinkCallback() {
        var attempt = service.begin("naver", NAVER_REDIRECT);

        assertThatThrownBy(
                        () ->
                                service.consumeLink(
                                        SocialProvider.NAVER,
                                        NAVER_REDIRECT,
                                        attempt.state(),
                                        attempt.state(),
                                        7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }

    @Test
    void consumesLinkStateBoundToTheSameMember() {
        var attempt = service.beginLink("naver", NAVER_REDIRECT, 7L);

        service.consumeLink(
                SocialProvider.NAVER, NAVER_REDIRECT, attempt.state(), attempt.state(), 7L);

        assertThatThrownBy(
                        () ->
                                service.consumeLink(
                                        SocialProvider.NAVER,
                                        NAVER_REDIRECT,
                                        attempt.state(),
                                        attempt.state(),
                                        7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
    }
}
