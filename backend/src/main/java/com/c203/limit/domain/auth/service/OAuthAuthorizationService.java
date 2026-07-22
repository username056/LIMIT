package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.config.AuthCookieProperties;
import com.c203.limit.domain.auth.config.SocialOAuthProperties;
import com.c203.limit.domain.auth.dto.response.OAuthAuthorizationResponse;
import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OAuthAuthorizationService {
    private final SocialOAuthProperties properties;
    private final AuthCookieProperties cookieProperties;
    private final OAuthAttemptStore attemptStore;

    public OAuthAuthorizationService(
            SocialOAuthProperties properties,
            AuthCookieProperties cookieProperties,
            OAuthAttemptStore attemptStore) {
        this.properties = properties;
        this.cookieProperties = cookieProperties;
        this.attemptStore = attemptStore;
    }

    public AuthorizationAttempt begin(String providerValue, String redirectUri) {
        return begin(providerValue, redirectUri, null);
    }

    public AuthorizationAttempt beginLink(String providerValue, String redirectUri, Long memberId) {
        return begin(providerValue, redirectUri, memberId);
    }

    private AuthorizationAttempt begin(String providerValue, String redirectUri, Long memberId) {
        SocialProvider provider = provider(providerValue);
        SocialOAuthProperties.Provider config = config(provider);
        if (!config.isEnabled()
                || !StringUtils.hasText(config.getClientId())
                || !isAllowedRedirectUri(config, redirectUri)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
        String state = OpaqueTokenSupport.generate();
        attemptStore.save(
                OpaqueTokenSupport.hash(state),
                new OAuthAttemptStore.OAuthAttempt(provider, redirectUri, memberId),
                cookieProperties.getOauthStateTtl());
        return new AuthorizationAttempt(
                new OAuthAuthorizationResponse(
                        authorizationUrl(provider, config, redirectUri, state)),
                state);
    }

    public void consume(
            SocialProvider provider, String redirectUri, String returnedState, String cookieState) {
        consume(provider, redirectUri, returnedState, cookieState, null);
    }

    public void consumeLink(
            SocialProvider provider,
            String redirectUri,
            String returnedState,
            String cookieState,
            Long memberId) {
        consume(provider, redirectUri, returnedState, cookieState, memberId);
    }

    private void consume(
            SocialProvider provider,
            String redirectUri,
            String returnedState,
            String cookieState,
            Long memberId) {
        if (!StringUtils.hasText(returnedState)
                || !StringUtils.hasText(cookieState)
                || !constantTimeEquals(returnedState, cookieState)) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        OAuthAttemptStore.OAuthAttempt attempt =
                attemptStore
                        .consume(OpaqueTokenSupport.hash(returnedState))
                        .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED));
        if (attempt.provider() != provider
                || !attempt.redirectUri().equals(redirectUri)
                || !java.util.Objects.equals(attempt.memberId(), memberId)) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    private String authorizationUrl(
            SocialProvider provider,
            SocialOAuthProperties.Provider config,
            String redirectUri,
            String state) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(authorizationUri(provider))
                        .queryParam("client_id", config.getClientId())
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("response_type", "code")
                        .queryParam("state", state);
        if (provider == SocialProvider.GOOGLE) {
            builder.queryParam("scope", "openid email profile")
                    .queryParam("prompt", "select_account");
        }
        return builder.build().encode().toUriString();
    }

    private String authorizationUri(SocialProvider provider) {
        return switch (provider) {
            case GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth";
            case KAKAO -> "https://kauth.kakao.com/oauth/authorize";
            case NAVER -> "https://nid.naver.com/oauth2.0/authorize";
        };
    }

    private SocialOAuthProperties.Provider config(SocialProvider provider) {
        return switch (provider) {
            case GOOGLE -> properties.getGoogle();
            case KAKAO -> properties.getKakao();
            case NAVER -> properties.getNaver();
        };
    }

    private boolean isAllowedRedirectUri(
            SocialOAuthProperties.Provider config, String redirectUri) {
        return StringUtils.hasText(redirectUri)
                && StringUtils.hasText(config.getRedirectUris())
                && Arrays.stream(config.getRedirectUris().split(","))
                        .map(String::trim)
                        .anyMatch(redirectUri::equals);
    }

    private SocialProvider provider(String value) {
        try {
            return SocialProvider.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public record AuthorizationAttempt(OAuthAuthorizationResponse response, String state) {}
}
