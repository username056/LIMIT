package com.c203.limit.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "limit.auth.oauth")
public class SocialOAuthProperties {
    private final Provider google = new Provider();
    private final Provider kakao = new Provider();
    private final Provider naver = new Provider();

    public Provider getGoogle() {
        return google;
    }

    public Provider getKakao() {
        return kakao;
    }

    public Provider getNaver() {
        return naver;
    }

    public static class Provider {
        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String redirectUris;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUris() {
            return redirectUris;
        }

        public void setRedirectUris(String redirectUris) {
            this.redirectUris = redirectUris;
        }
    }
}
