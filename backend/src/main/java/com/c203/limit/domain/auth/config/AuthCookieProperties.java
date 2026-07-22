package com.c203.limit.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "limit.auth.cookie")
public class AuthCookieProperties {
    private String refreshName = "limit_refresh";
    private String socialSignupName = "limit_social_signup";
    private String oauthStateName = "limit_oauth_state";
    private boolean secure;
    private String sameSite = "Lax";
    private Duration socialSignupTtl = Duration.ofMinutes(15);
    private Duration oauthStateTtl = Duration.ofMinutes(10);

    public String getRefreshName() {
        return refreshName;
    }

    public void setRefreshName(String refreshName) {
        this.refreshName = refreshName;
    }

    public String getSocialSignupName() {
        return socialSignupName;
    }

    public void setSocialSignupName(String socialSignupName) {
        this.socialSignupName = socialSignupName;
    }

    public String getOauthStateName() {
        return oauthStateName;
    }

    public void setOauthStateName(String oauthStateName) {
        this.oauthStateName = oauthStateName;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public Duration getSocialSignupTtl() {
        return socialSignupTtl;
    }

    public void setSocialSignupTtl(Duration socialSignupTtl) {
        this.socialSignupTtl = socialSignupTtl;
    }

    public Duration getOauthStateTtl() {
        return oauthStateTtl;
    }

    public void setOauthStateTtl(Duration oauthStateTtl) {
        this.oauthStateTtl = oauthStateTtl;
    }
}
