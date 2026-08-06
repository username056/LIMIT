package com.c203.limit.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.c203.limit.domain.auth.config.SocialOAuthProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.io.IOException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

    @Test
    void rejectsBlankAuthorizationCodeBeforeCallingProvider() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());

        assertThatThrownBy(() -> client.exchange("   ", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        assertThatThrownBy(() -> client.exchange(null, REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void rejectsBlankRedirectUri() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());

        assertThatThrownBy(() -> client.exchange("google-code", "   ", "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void rejectsProviderWithoutConfiguredClientId() {
        var properties = new SocialOAuthProperties();
        properties.getGoogle().setClientSecret("client-secret");
        properties.getGoogle().setRedirectUris(REDIRECT);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties);

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenGoogleTokenEndpointReturnsEmptyBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenGoogleUserInfoReturnsEmptyBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"google-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenNaverUserInfoReturnsEmptyBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverIdentityClient(builder, properties());
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"naver-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("naver-code", REDIRECT, "naver-state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void rejectsProviderWithoutConfiguredClientSecret() {
        var properties = new SocialOAuthProperties();
        properties.getGoogle().setClientId("client-id");
        properties.getGoogle().setRedirectUris(REDIRECT);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties);

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void rejectsProviderWithoutConfiguredRedirectAllowlist() {
        var properties = new SocialOAuthProperties();
        properties.getGoogle().setClientId("client-id");
        properties.getGoogle().setClientSecret("client-secret");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties);

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void acceptsRedirectUriListedAnywhereInCommaSeparatedAllowlist() {
        var properties = new SocialOAuthProperties();
        properties.getGoogle().setClientId("client-id");
        properties.getGoogle().setClientSecret("client-secret");
        properties.getGoogle().setRedirectUris("https://other.example/callback , " + REDIRECT);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties);
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"google-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(
                        withSuccess(
                                "{\"sub\":\"g-2\",\"email\":\"user@example.com\",\"email_verified\":true}",
                                MediaType.APPLICATION_JSON));

        var identity = client.exchange("google-code", REDIRECT, "state");

        assertThat(identity.providerUserId()).isEqualTo("g-2");
        assertThat(identity.nickname()).isNull();
        server.verify();
    }

    @Test
    void failsWhenGoogleTokenResponseOmitsAccessToken() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"error\":\"invalid_grant\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenGoogleReportsUnverifiedEmail() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"google-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(
                        withSuccess(
                                "{\"sub\":\"g-1\",\"email\":\"user@example.com\",\"email_verified\":false}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenGoogleUserInfoOmitsEmailVerifiedFlag() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"google-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(
                        withSuccess(
                                "{\"sub\":\"g-1\",\"email\":\"user@example.com\"}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenGoogleTokenEndpointReturnsServerError() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenGoogleUserInfoRejectsTheAccessToken() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"google-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenGoogleTokenEndpointIsUnreachable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GoogleIdentityClient(builder, properties());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.exchange("google-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenKakaoProfileOmitsAccountObject() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new KakaoIdentityClient(builder, properties());
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("kakao-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenKakaoEmailIsValidButNotVerified() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new KakaoIdentityClient(builder, properties());
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(
                        withSuccess(
                                "{\"id\":123,\"kakao_account\":{\"email\":\"user@kakao.com\",\"is_email_valid\":true,\"is_email_verified\":false}}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("kakao-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenKakaoAccountOmitsProfileObject() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new KakaoIdentityClient(builder, properties());
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(
                        withSuccess(
                                "{\"id\":123,\"kakao_account\":{\"email\":\"user@kakao.com\",\"is_email_valid\":true,\"is_email_verified\":true}}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("kakao-code", REDIRECT, "state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void failsWhenNaverResponseEnvelopeIsMissing() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverIdentityClient(builder, properties());
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"naver-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(
                        withSuccess(
                                "{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("naver-code", REDIRECT, "naver-state"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        server.verify();
    }

    @Test
    void fallsBackToNaverNameWhenNicknameIsBlank() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverIdentityClient(builder, properties());
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"naver-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(
                        withSuccess(
                                "{\"response\":{\"id\":\"n-2\",\"email\":\"user@naver.com\",\"nickname\":\"\",\"name\":\"Naver Name\"}}",
                                MediaType.APPLICATION_JSON));

        var identity = client.exchange("naver-code", REDIRECT, "naver-state");

        assertThat(identity.nickname()).isEqualTo("Naver Name");
        server.verify();
    }

    @Test
    void failsWhenNaverProfileOmitsEmail() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverIdentityClient(builder, properties());
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"naver-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(
                        withSuccess(
                                "{\"response\":{\"id\":\"n-3\",\"nickname\":\"Naver User\"}}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("naver-code", REDIRECT, "naver-state"))
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
