package com.c203.limit.domain.auth.client;

import com.c203.limit.domain.auth.config.SocialOAuthProperties;
import com.c203.limit.domain.auth.entity.SocialProvider;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "limit.auth.oauth.kakao", name = "enabled", havingValue = "true")
public class KakaoIdentityClient extends AbstractOAuthIdentityClient {
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    public KakaoIdentityClient(
            @Qualifier("socialOAuthRestClientBuilder") RestClient.Builder restClientBuilder,
            SocialOAuthProperties properties) {
        super(restClientBuilder, properties.getKakao());
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialIdentity exchange(String authorizationCode, String redirectUri, String state) {
        validate(authorizationCode, redirectUri);
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "authorization_code");
            form.add("code", authorizationCode);
            form.add("client_id", properties.getClientId());
            form.add("client_secret", properties.getClientSecret());
            form.add("redirect_uri", redirectUri);
            Map<String, Object> token =
                    restClient
                            .post()
                            .uri(TOKEN_URI)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(mapType());
            Map<String, Object> profile = userInfo(requiredText(token, "access_token"));
            Map<String, Object> account = requiredMap(profile, "kakao_account");
            if (!booleanValue(account, "is_email_valid")
                    || !booleanValue(account, "is_email_verified")) {
                throw authenticationFailed();
            }
            return new SocialIdentity(
                    requiredText(profile, "id"),
                    requiredText(account, "email"),
                    optionalText(requiredMap(account, "profile"), "nickname"));
        } catch (RestClientException exception) {
            throw authenticationFailed();
        }
    }

    private Map<String, Object> userInfo(String accessToken) {
        return restClient
                .get()
                .uri(USER_INFO_URI)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(mapType());
    }
}
