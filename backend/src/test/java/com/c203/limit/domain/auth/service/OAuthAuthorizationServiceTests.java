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
    private OAuthAuthorizationService service;

    @BeforeEach
    void setUp() {
        SocialOAuthProperties properties = new SocialOAuthProperties();
        properties.getNaver().setEnabled(true);
        properties.getNaver().setClientId("naver-client");
        properties.getNaver().setRedirectUris("http://localhost:5173/auth/callback/naver");
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
}
