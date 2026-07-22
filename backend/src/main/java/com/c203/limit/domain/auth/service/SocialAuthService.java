package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.client.SocialIdentityClient;
import com.c203.limit.domain.auth.dto.request.CompleteSocialSignupRequest;
import com.c203.limit.domain.auth.dto.response.SocialAccountResponse;
import com.c203.limit.domain.auth.entity.SocialAccount;
import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAuthService {
    private final Map<SocialProvider, SocialIdentityClient> clients;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountLoginService socialAccountLoginService;
    private final OAuthAuthorizationService authorizationService;

    public SocialAuthService(
            List<SocialIdentityClient> clients,
            SocialAccountRepository socialAccountRepository,
            SocialAccountLoginService socialAccountLoginService,
            OAuthAuthorizationService authorizationService) {
        this.clients =
                clients.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        SocialIdentityClient::provider, Function.identity()));
        this.socialAccountRepository = socialAccountRepository;
        this.socialAccountLoginService = socialAccountLoginService;
        this.authorizationService = authorizationService;
    }

    public SocialAccountLoginService.SocialLoginResult login(
            String providerValue,
            String code,
            String redirectUri,
            String state,
            String cookieState) {
        SocialProvider provider;
        try {
            provider = SocialProvider.valueOf(providerValue.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
        SocialIdentityClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
        authorizationService.consume(provider, redirectUri, state, cookieState);
        var identity = client.exchange(code, redirectUri, state);
        return socialAccountLoginService.login(provider, identity);
    }

    public SocialAccountLoginService.SocialLoginResult completeSignup(
            String signupToken, CompleteSocialSignupRequest request) {
        return socialAccountLoginService.complete(signupToken, request);
    }

    public SocialAccountResponse link(
            Long memberId,
            String providerValue,
            String code,
            String redirectUri,
            String state,
            String cookieState) {
        SocialProvider provider = provider(providerValue);
        SocialIdentityClient client = client(provider);
        authorizationService.consumeLink(provider, redirectUri, state, cookieState, memberId);
        SocialAccountResponse account =
                socialAccountLoginService.link(
                        memberId, provider, client.exchange(code, redirectUri, state));
        return new SocialAccountResponse(
                account.getSocialAccountId(),
                account.getProvider(),
                maskEmail(account.getProviderEmail()),
                account.getConnectedAt());
    }

    @Transactional(readOnly = true)
    public List<SocialAccountResponse> accounts(Long memberId) {
        return socialAccountRepository.findAllByMemberId(memberId).stream()
                .map(
                        account ->
                                new SocialAccountResponse(
                                        account.getId(),
                                        account.getProvider().name(),
                                        maskEmail(account.getProviderEmail()),
                                        account.getLinkedAt()))
                .toList();
    }

    @Transactional
    public void unlink(Long memberId, Long socialAccountId) {
        SocialAccount account =
                socialAccountRepository
                        .findByIdAndMemberId(socialAccountId, memberId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND));
        Member member = account.getMember();
        if (member.getPassword() == null
                && socialAccountRepository.countByMemberId(memberId) <= 1) {
            throw new BusinessException(ErrorCode.LAST_LOGIN_METHOD);
        }
        socialAccountRepository.delete(account);
    }

    private String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private SocialProvider provider(String providerValue) {
        try {
            return SocialProvider.valueOf(providerValue.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
    }

    private SocialIdentityClient client(SocialProvider provider) {
        SocialIdentityClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
        return client;
    }
}
