package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.client.SocialIdentityClient;
import com.c203.limit.domain.auth.config.AuthCookieProperties;
import com.c203.limit.domain.auth.dto.request.CompleteSocialSignupRequest;
import com.c203.limit.domain.auth.dto.response.SocialAccountResponse;
import com.c203.limit.domain.auth.dto.response.SocialLoginResponse;
import com.c203.limit.domain.auth.dto.response.SocialSignupPreviewResponse;
import com.c203.limit.domain.auth.entity.SocialAccount;
import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.member.dto.response.MemberSummaryResponse;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.entity.MemberStatus;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SocialAccountLoginService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final AuthService authService;
    private final SocialSignupSessionStore signupSessionStore;
    private final AuthCookieProperties cookieProperties;
    private final TermsAgreementService termsAgreementService;

    public SocialAccountLoginService(
            SocialAccountRepository socialAccountRepository,
            MemberRepository memberRepository,
            AuthService authService,
            SocialSignupSessionStore signupSessionStore,
            AuthCookieProperties cookieProperties,
            TermsAgreementService termsAgreementService) {
        this.socialAccountRepository = socialAccountRepository;
        this.memberRepository = memberRepository;
        this.authService = authService;
        this.signupSessionStore = signupSessionStore;
        this.cookieProperties = cookieProperties;
        this.termsAgreementService = termsAgreementService;
    }

    @Transactional
    public SocialLoginResult login(
            SocialProvider provider, SocialIdentityClient.SocialIdentity identity) {
        String email = normalizeEmail(identity.email());
        var linked =
                socialAccountRepository.findByProviderAndProviderUserId(
                        provider, identity.providerUserId());
        if (linked.isPresent()) {
            Member member = linked.get().getMember();
            if (member.getStatus() != MemberStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.MEMBER_NOT_ACTIVE);
            }
            member.recordLogin();
            SessionResult<com.c203.limit.domain.auth.dto.response.TokenResponse> token =
                    authService.issueTokens(member);
            return new SocialLoginResult(
                    authenticated(member, token.body()), token.refreshToken(), null);
        }
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_REAUTH_REQUIRED);
        }

        String signupToken = OpaqueTokenSupport.generate();
        String suggestedNickname = uniqueNickname(identity.nickname());
        signupSessionStore.save(
                OpaqueTokenSupport.hash(signupToken),
                new SocialSignupSessionStore.SocialSignupSession(
                        provider, identity.providerUserId(), email, suggestedNickname),
                cookieProperties.getSocialSignupTtl());
        return new SocialLoginResult(
                new SocialLoginResponse(
                        "SIGNUP_REQUIRED",
                        null,
                        null,
                        null,
                        null,
                        new SocialSignupPreviewResponse(provider.name(), email, suggestedNickname)),
                null,
                signupToken);
    }

    @Transactional
    public SocialLoginResult complete(String signupToken, CompleteSocialSignupRequest request) {
        if (!StringUtils.hasText(signupToken)) {
            throw new BusinessException(ErrorCode.SOCIAL_SIGNUP_SESSION_INVALID);
        }
        authService.validateNickname(request.nickname());
        TermsAgreementService.TermsConsent consent =
                new TermsAgreementService.TermsConsent(
                        request.serviceTermsAccepted(),
                        request.privacyTermsAccepted(),
                        request.ageRequirementAccepted(),
                        request.marketingAccepted());
        termsAgreementService.validate(consent);
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        SocialSignupSessionStore.SocialSignupSession session =
                signupSessionStore
                        .consume(OpaqueTokenSupport.hash(signupToken))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.SOCIAL_SIGNUP_SESSION_INVALID));
        if (memberRepository.existsByEmailIgnoreCase(session.email())) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_REAUTH_REQUIRED);
        }
        if (socialAccountRepository
                .findByProviderAndProviderUserId(session.provider(), session.providerUserId())
                .isPresent()) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CONFLICT);
        }

        Member member =
                memberRepository.save(
                        Member.createSocial(
                                session.email(),
                                request.nickname(),
                                request.phone(),
                                request.marketingAccepted()));
        termsAgreementService.record(member, consent);
        try {
            socialAccountRepository.saveAndFlush(
                    SocialAccount.link(
                            member, session.provider(), session.providerUserId(), session.email()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CONFLICT);
        }
        member.recordLogin();
        SessionResult<com.c203.limit.domain.auth.dto.response.TokenResponse> token =
                authService.issueTokens(member);
        return new SocialLoginResult(
                authenticated(member, token.body()), token.refreshToken(), null);
    }

    @Transactional
    public SocialAccountResponse link(
            Long memberId, SocialProvider provider, SocialIdentityClient.SocialIdentity identity) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_ACTIVE);
        }
        if (socialAccountRepository.existsByMemberIdAndProvider(memberId, provider)
                || socialAccountRepository
                        .findByProviderAndProviderUserId(provider, identity.providerUserId())
                        .isPresent()) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CONFLICT);
        }
        SocialAccount account;
        try {
            account =
                    socialAccountRepository.saveAndFlush(
                            SocialAccount.link(
                                    member,
                                    provider,
                                    identity.providerUserId(),
                                    normalizeEmail(identity.email())));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CONFLICT);
        }
        return new SocialAccountResponse(
                account.getId(),
                provider.name(),
                account.getProviderEmail(),
                account.getLinkedAt());
    }

    private SocialLoginResponse authenticated(
            Member member, com.c203.limit.domain.auth.dto.response.TokenResponse token) {
        return new SocialLoginResponse(
                "AUTHENTICATED",
                token.getAccessToken(),
                token.getTokenType(),
                token.getExpiresIn(),
                new MemberSummaryResponse(member.getId(), member.getNickname(), Set.of("MEMBER")),
                null);
    }

    private String normalizeEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String uniqueNickname(String suggestion) {
        String base =
                suggestion == null || suggestion.isBlank()
                        ? "member"
                        : suggestion.replaceAll("[^가-힣a-zA-Z0-9_]", "");
        if (base.length() < 2) {
            base = "member";
        }
        if (base.length() > 14) {
            base = base.substring(0, 14);
        }
        String candidate = base;
        int suffix = 1;
        while (memberRepository.existsByNickname(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    public record SocialLoginResult(
            SocialLoginResponse response, String refreshToken, String signupToken) {}
}
