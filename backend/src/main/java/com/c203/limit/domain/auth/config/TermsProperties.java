package com.c203.limit.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "limit.auth.terms")
public class TermsProperties {
    private String serviceVersion = "2026-07-22";
    private String privacyVersion = "2026-07-22";
    private String ageVersion = "2026-07-22";
    private String marketingVersion = "2026-07-22";

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public String getPrivacyVersion() {
        return privacyVersion;
    }

    public void setPrivacyVersion(String privacyVersion) {
        this.privacyVersion = privacyVersion;
    }

    public String getAgeVersion() {
        return ageVersion;
    }

    public void setAgeVersion(String ageVersion) {
        this.ageVersion = ageVersion;
    }

    public String getMarketingVersion() {
        return marketingVersion;
    }

    public void setMarketingVersion(String marketingVersion) {
        this.marketingVersion = marketingVersion;
    }
}
