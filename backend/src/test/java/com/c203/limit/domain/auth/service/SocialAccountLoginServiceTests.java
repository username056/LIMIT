package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.auth.client.SocialIdentityClient;
import com.c203.limit.domain.auth.config.AuthCookieProperties;
import com.c203.limit.domain.auth.dto.request.CompleteSocialSignupRequest;
import com.c203.limit.domain.auth.dto.response.TokenResponse;
import com.c203.limit.domain.auth.entity.SocialAccount;
import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.entity.MemberStatus;
import com.c203.limit.domain.member.dto.response.MemberSummaryResponse;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SocialAccountLoginServiceTests {
    @Mock SocialAccountRepository socialAccountRepository;
    @Mock MemberRepository memberRepository;
    @Mock AuthService authService;
    @Mock SocialSignupSessionStore signupSessionStore;
    @Mock TermsAgreementService termsAgreementService;
    SocialAccountLoginService service;

    @BeforeEach
    void setUp() {
        service =
                new SocialAccountLoginService(
                        socialAccountRepository,
                        memberRepository,
                        authService,
                        signupSessionStore,
                        new AuthCookieProperties(),
                        termsAgreementService);
    }

    @Test
    void returnsExistingSocialMemberAndRecordsLogin() {
        Member member = Member.createSocial("user@example.com", "member");
        ReflectionTestUtils.setField(member, "id", 1L);
        SocialAccount account =
                SocialAccount.link(member, SocialProvider.GOOGLE, "g-1", "user@example.com");
        when(socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.GOOGLE, "g-1"))
                .thenReturn(Optional.of(account));
        when(authService.issueTokens(member))
                .thenReturn(
                        new SessionResult<>(
                                new TokenResponse(
                                                "access",
                                                "Bearer",
                                                1800,
                                                new MemberSummaryResponse(
                                                        1L,
                                                        "member",
                                                        java.util.Set.of("MEMBER"),
                                                        null)),
                                        "refresh"));

        var result =
                service.login(
                        SocialProvider.GOOGLE,
                        new SocialIdentityClient.SocialIdentity(
                                "g-1", "user@example.com", "member"));

        assertThat(result.response().getStatus()).isEqualTo("AUTHENTICATED");
        assertThat(result.response().getAccessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(member.getLastLoginAt()).isNotNull();
    }

    @Test
    void startsSignupSessionWithoutCreatingMember() {
        when(socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.NAVER, "n-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("user@naver.com")).thenReturn(false);

        var result =
                service.login(
                        SocialProvider.NAVER,
                        new SocialIdentityClient.SocialIdentity(
                                "n-1", " User@Naver.com ", "네이버사용자"));

        assertThat(result.response().getStatus()).isEqualTo("SIGNUP_REQUIRED");
        assertThat(result.response().getSignup().email()).isEqualTo("user@naver.com");
        assertThat(result.signupToken()).isNotBlank();
        verify(signupSessionStore).save(any(), any(), any());
        verify(memberRepository, never()).save(any());
        verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void requiresExistingLoginBeforeLinkingMatchingEmail() {
        when(socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.GOOGLE, "g-3"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.login(
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-3", "user@example.com", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_REAUTH_REQUIRED));
    }

    @Test
    void completesSocialSignupWithTermsAndLoginSession() {
        var pending =
                new SocialSignupSessionStore.SocialSignupSession(
                        SocialProvider.KAKAO, "k-1", "user@example.com", "member");
        when(signupSessionStore.consume(any())).thenReturn(Optional.of(pending));
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(
                        invocation -> {
                            Member member = invocation.getArgument(0);
                            ReflectionTestUtils.setField(member, "id", 4L);
                            return member;
                        });
        when(authService.issueTokens(any(Member.class)))
                .thenReturn(
                        new SessionResult<>(
                                new TokenResponse(
                                                "access",
                                                "Bearer",
                                                1800,
                                                new MemberSummaryResponse(
                                                        4L,
                                                        "runner",
                                                        java.util.Set.of("MEMBER"),
                                                        null)),
                                        "refresh"));

        var result =
                service.complete(
                        "signup-token",
                        new CompleteSocialSignupRequest(
                                "runner", null, true, true, true, false));

        assertThat(result.response().getStatus()).isEqualTo("AUTHENTICATED");
        assertThat(result.response().getMember().getNickname()).isEqualTo("runner");
        verify(termsAgreementService).record(any(Member.class), any());
        verify(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    void rejectsInactiveLinkedMember() {
        Member member = Member.createSocial("user@example.com", "member");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWN);
        SocialAccount account =
                SocialAccount.link(member, SocialProvider.GOOGLE, "g-1", "user@example.com");
        when(socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.GOOGLE, "g-1"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(
                        () ->
                                service.login(
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-1", "user@example.com", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_ACTIVE));
        verify(authService, never()).issueTokens(any());
    }
}
