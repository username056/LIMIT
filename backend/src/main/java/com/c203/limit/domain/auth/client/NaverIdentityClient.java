package com.c203.limit.domain.auth.client;

import com.c203.limit.domain.auth.config.SocialOAuthProperties;
import com.c203.limit.domain.auth.entity.SocialProvider;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "limit.auth.oauth.naver", name = "enabled", havingValue = "true")
public class NaverIdentityClient extends AbstractOAuthIdentityClient {
    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URI = "https://openapi.naver.com/v1/nid/me";

    public NaverIdentityClient(
            @Qualifier("socialOAuthRestClientBuilder") RestClient.Builder restClientBuilder,
            SocialOAuthProperties properties) {
        super(restClientBuilder, properties.getNaver());
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public SocialIdentity exchange(String authorizationCode, String redirectUri, String state) {
        validate(authorizationCode, redirectUri);
        if (!StringUtils.hasText(state)) {
            throw authenticationFailed();
        }
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "authorization_code");
            form.add("code", authorizationCode);
            form.add("state", state);
            form.add("client_id", properties.getClientId());
            form.add("client_secret", properties.getClientSecret());
            Map<String, Object> token =
                    restClient
                            .post()
                            .uri(TOKEN_URI)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(mapType());
            Map<String, Object> profile =
                    requiredMap(userInfo(requiredText(token, "access_token")), "response");
            String nickname = optionalText(profile, "nickname");
            if (!StringUtils.hasText(nickname)) {
                nickname = optionalText(profile, "name");
            }
            return new SocialIdentity(
                    requiredText(profile, "id"), requiredText(profile, "email"), nickname);
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
