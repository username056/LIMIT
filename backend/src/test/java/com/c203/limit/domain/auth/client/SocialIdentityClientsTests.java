package com.c203.limit.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.c203.limit.domain.auth.config.SocialOAuthProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SocialIdentityClientsTests {
    private static final String REDIRECT = "http://localhost:5173/auth/callback/test";

    @Test
    void exchangesGoogleCodeForVerifiedIdentity() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("code=google-code")))
                .andRespond(withSuccess("{\"access_token\":\"google-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(header("Authorization", "Bearer google-token"))
                .andRespond(
                        withSuccess(
                                "{\"sub\":\"g-1\",\"email\":\"user@example.com\",\"name\":\"User\",\"email_verified\":true}",
                                MediaType.APPLICATION_JSON));

        var identity = client.exchange("google-code", REDIRECT, "state");

        assertThat(identity.providerUserId()).isEqualTo("g-1");
        assertThat(identity.email()).isEqualTo("user@example.com");
        server.verify();
    }

    @Test
    void exchangesKakaoCodeForVerifiedIdentity() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new KakaoIdentityClient(builder, properties());
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header("Authorization", "Bearer kakao-token"))
                .andRespond(
                        withSuccess(
                                "{\"id\":123,\"kakao_account\":{\"email\":\"user@kakao.com\",\"is_email_valid\":true,\"is_email_verified\":true,\"profile\":{\"nickname\":\"Kakao User\"}}}",
                                MediaType.APPLICATION_JSON));

        var identity = client.exchange("kakao-code", REDIRECT, "state");

        assertThat(identity.providerUserId()).isEqualTo("123");
        assertThat(identity.nickname()).isEqualTo("Kakao User");
        server.verify();
    }

    @Test
    void exchangesNaverCodeAndStateForIdentity() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverIdentityClient(builder, properties());
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andExpect(content().string(Matchers.containsString("state=naver-state")))
                .andRespond(withSuccess("{\"access_token\":\"naver-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andExpect(header("Authorization", "Bearer naver-token"))
                .andRespond(
                        withSuccess(
                                "{\"response\":{\"id\":\"n-1\",\"email\":\"user@naver.com\",\"nickname\":\"Naver User\"}}",
                                MediaType.APPLICATION_JSON));

        var identity = client.exchange("naver-code", REDIRECT, "naver-state");

        assertThat(identity.providerUserId()).isEqualTo("n-1");
        assertThat(identity.email()).isEqualTo("user@naver.com");
        server.verify();
    }

    @Test
    void rejectsRedirectUriOutsideAllowlistBeforeCallingProvider() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());

        assertThatThrownBy(
                        () ->
                                client.exchange(
                                        "google-code", "https://attacker.example/callback", "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void naverRequiresState() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverIdentityClient(builder, properties());

        assertThatThrownBy(() -> client.exchange("naver-code", REDIRECT, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    private SocialOAuthProperties properties() {
        var properties = new SocialOAuthProperties();
        configure(properties.getGoogle());
        configure(properties.getKakao());
        configure(properties.getNaver());
        return properties;
    }

    private void configure(SocialOAuthProperties.Provider provider) {
        provider.setClientId("client-id");
        provider.setClientSecret("client-secret");
        provider.setRedirectUris(REDIRECT);
    }
}
