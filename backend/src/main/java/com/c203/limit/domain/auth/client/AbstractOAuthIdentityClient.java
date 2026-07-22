package com.c203.limit.domain.auth.client;

import com.c203.limit.domain.auth.config.SocialOAuthProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

abstract class AbstractOAuthIdentityClient implements SocialIdentityClient {
    protected final RestClient restClient;
    protected final SocialOAuthProperties.Provider properties;

    protected AbstractOAuthIdentityClient(
            RestClient.Builder restClientBuilder, SocialOAuthProperties.Provider properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    protected void validate(String authorizationCode, String redirectUri) {
        if (!StringUtils.hasText(authorizationCode)
                || !StringUtils.hasText(properties.getClientId())
                || !StringUtils.hasText(properties.getClientSecret())
                || !isAllowedRedirectUri(redirectUri)) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    protected String requiredText(Map<String, Object> source, String field) {
        Object rawValue = source == null ? null : source.get(field);
        String value = rawValue == null ? null : rawValue.toString();
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> requiredMap(Map<String, Object> source, String field) {
        Object value = source == null ? null : source.get(field);
        if (!(value instanceof Map<?, ?>)) {
            throw authenticationFailed();
        }
        return (Map<String, Object>) value;
    }

    protected String optionalText(Map<String, Object> source, String field) {
        Object value = source == null ? null : source.get(field);
        return value == null ? null : value.toString();
    }

    protected boolean booleanValue(Map<String, Object> source, String field) {
        Object value = source == null ? null : source.get(field);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    protected BusinessException authenticationFailed() {
        return new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
    }

    protected ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {};
    }

    private boolean isAllowedRedirectUri(String redirectUri) {
        if (!StringUtils.hasText(redirectUri)
                || !StringUtils.hasText(properties.getRedirectUris())) {
            return false;
        }
        return Arrays.stream(properties.getRedirectUris().split(","))
                .map(String::trim)
                .anyMatch(redirectUri::equals);
    }
}
