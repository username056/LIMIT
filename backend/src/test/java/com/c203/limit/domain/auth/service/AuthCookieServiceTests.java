package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.auth.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 발급되는 Set-Cookie 문자열의 속성(HttpOnly/Secure/SameSite/Path/Max-Age)과, 요청에서 쿠키 값을
 * 읽어오는 value()의 null-cookies / 이름 불일치 처리 분기를 확인한다.
 */
class AuthCookieServiceTests {

    private AuthCookieProperties properties;
    private AuthCookieService service;

    @BeforeEach
    void setUp() {
        properties = new AuthCookieProperties();
        service = new AuthCookieService(properties, Duration.ofDays(14));
    }

    @Test
    void refreshCookieCarriesTokenNameAndPathWithConfiguredTtl() {
        String cookie = service.refresh("refresh-token-value");

        assertThat(cookie).contains("limit_refresh=refresh-token-value");
        assertThat(cookie).contains("Path=/api/v1/auth");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Max-Age=1209600"); // 14 days in seconds
    }

    @Test
    void clearRefreshExpiresImmediatelyWithEmptyValue() {
        String cookie = service.clearRefresh();

        assertThat(cookie).contains("limit_refresh=");
        assertThat(cookie).contains("Max-Age=0");
    }

    @Test
    void socialSignupCookieUsesItsOwnNamePathAndTtl() {
        String cookie = service.socialSignup("signup-token");

        assertThat(cookie).contains("limit_social_signup=signup-token");
        assertThat(cookie).contains("Path=/api/v1/auth/social-signups");
        assertThat(cookie).contains("Max-Age=900"); // 15 minutes
    }

    @Test
    void oauthStateCookieUsesItsOwnNamePathAndTtl() {
        String cookie = service.oauthState("state-value");

        assertThat(cookie).contains("limit_oauth_state=state-value");
        assertThat(cookie).contains("Path=/api/v1");
        assertThat(cookie).contains("Max-Age=600"); // 10 minutes
    }

    @Test
    void cookieReflectsSecureAndSameSitePropertiesWhenConfigured() {
        properties.setSecure(true);
        properties.setSameSite("None");

        String cookie = service.refresh("token");

        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("SameSite=None");
    }

    @Test
    void refreshTokenReturnsNullWhenRequestHasNoCookies() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getCookies()).thenReturn(null);

        assertThat(service.refreshToken(request)).isNull();
    }

    @Test
    void refreshTokenReturnsNullWhenNoCookieMatchesTheConfiguredName() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("other_cookie", "value")});

        assertThat(service.refreshToken(request)).isNull();
    }

    @Test
    void refreshTokenExtractsValueFromMatchingCookieAmongOthers() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getCookies())
                .thenReturn(new Cookie[] {
                    new Cookie("other_cookie", "irrelevant"), new Cookie("limit_refresh", "the-token")
                });

        assertThat(service.refreshToken(request)).isEqualTo("the-token");
    }

    @Test
    void socialSignupTokenAndOauthStateTokenReadTheirOwnCookieNames() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getCookies())
                .thenReturn(new Cookie[] {
                    new Cookie("limit_social_signup", "signup-value"),
                    new Cookie("limit_oauth_state", "state-value")
                });

        assertThat(service.socialSignupToken(request)).isEqualTo("signup-value");
        assertThat(service.oauthStateToken(request)).isEqualTo("state-value");
    }
}
