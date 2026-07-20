package com.c203.limit.domain.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.c203.limit.domain.auth.client.SocialIdentityClient;
import com.c203.limit.domain.auth.dto.response.SocialAccountResponse;
import com.c203.limit.domain.auth.dto.response.SocialLoginResponse;
import com.c203.limit.domain.auth.entity.SocialAccount;
import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.member.dto.response.MemberSummaryResponse;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;

@Service
public class SocialAuthService {
    private final Map<SocialProvider, SocialIdentityClient> clients;
    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final AuthService authService;
    public SocialAuthService(List<SocialIdentityClient> clients, SocialAccountRepository socialAccountRepository,
            MemberRepository memberRepository, AuthService authService) {
        this.clients = clients.stream().collect(Collectors.toUnmodifiableMap(SocialIdentityClient::provider, Function.identity()));
        this.socialAccountRepository = socialAccountRepository;
        this.memberRepository = memberRepository;
        this.authService = authService;
    }
    @Transactional
    public SocialLoginResponse login(String providerValue, String code, String redirectUri) {
        SocialProvider provider;
        try { provider = SocialProvider.valueOf(providerValue.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER); }
        SocialIdentityClient client = clients.get(provider);
        if (client == null) throw new BusinessException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        var identity = client.exchange(code, redirectUri);
        var linked = socialAccountRepository.findByProviderAndProviderUserId(provider, identity.providerUserId());
        boolean isNew = linked.isEmpty();
        Member member = linked.map(SocialAccount::getMember).orElseGet(() -> createMember(provider, identity));
        var token = authService.issueTokens(member);
        return new SocialLoginResponse(isNew, token.getAccessToken(), token.getRefreshToken(),
                new MemberSummaryResponse(member.getId(), member.getNickname(), Set.of(member.getRole().name())));
    }
    @Transactional(readOnly = true)
    public List<SocialAccountResponse> accounts(Long memberId) {
        return socialAccountRepository.findAllByMemberId(memberId).stream()
                .map(account -> new SocialAccountResponse(account.getId(), account.getProvider().name(),
                        maskEmail(account.getProviderEmail()), account.getLinkedAt())).toList();
    }
    @Transactional
    public void unlink(Long memberId, Long socialAccountId) {
        SocialAccount account = socialAccountRepository.findByIdAndMemberId(socialAccountId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND));
        Member member = account.getMember();
        if (member.getPassword() == null && socialAccountRepository.countByMemberId(memberId) <= 1) {
            throw new BusinessException(ErrorCode.LAST_LOGIN_METHOD);
        }
        socialAccountRepository.delete(account);
    }
    private Member createMember(SocialProvider provider, SocialIdentityClient.SocialIdentity identity) {
        Member existing = memberRepository.findByEmailIgnoreCase(identity.email()).orElse(null);
        Member member = existing != null ? existing : memberRepository.save(Member.createSocial(identity.email(), uniqueNickname(identity.nickname())));
        socialAccountRepository.save(SocialAccount.link(member, provider, identity.providerUserId(), identity.email()));
        return member;
    }
    private String uniqueNickname(String suggestion) {
        String base = suggestion == null || suggestion.isBlank() ? "member" : suggestion.replaceAll("[^가-힣a-zA-Z0-9_]", "");
        if (base.length() < 2) base = "member";
        if (base.length() > 14) base = base.substring(0, 14);
        String candidate = base;
        int suffix = 1;
        while (memberRepository.existsByNickname(candidate)) candidate = base + suffix++;
        return candidate;
    }
    private String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
