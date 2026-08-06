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
import org.springframework.dao.DataIntegrityViolationException;
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
                                                        null,
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
                                                        null,
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

    @Test
    void rejectsSocialIdentityWithoutUsableEmail() {
        assertThatThrownBy(
                        () ->
                                service.login(
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-9", "not-an-email", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        assertThatThrownBy(
                        () ->
                                service.login(
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-9", null, "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        verify(socialAccountRepository, never()).findByProviderAndProviderUserId(any(), any());
    }

    @Test
    void fallsBackToDefaultNicknameAndAppendsSuffixWhenSuggestionIsTaken() {
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-9"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByNickname("member")).thenReturn(true);
        when(memberRepository.existsByNickname("member1")).thenReturn(false);

        var result =
                service.login(
                        SocialProvider.KAKAO,
                        new SocialIdentityClient.SocialIdentity("k-9", "user@kakao.com", "!!!"));

        assertThat(result.response().getStatus()).isEqualTo("SIGNUP_REQUIRED");
        assertThat(result.response().getSignup().suggestedNickname()).isEqualTo("member1");
    }

    @Test
    void fallsBackToDefaultNicknameWhenProviderSendsNoNickname() {
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-11"))
                .thenReturn(Optional.empty());

        var withoutNickname =
                service.login(
                        SocialProvider.KAKAO,
                        new SocialIdentityClient.SocialIdentity("k-11", "user@kakao.com", null));
        var withBlankNickname =
                service.login(
                        SocialProvider.KAKAO,
                        new SocialIdentityClient.SocialIdentity("k-11", "user@kakao.com", "   "));

        assertThat(withoutNickname.response().getSignup().suggestedNickname()).isEqualTo("member");
        assertThat(withBlankNickname.response().getSignup().suggestedNickname()).isEqualTo("member");
    }

    @Test
    void truncatesOverlongProviderNicknameSuggestion() {
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-10"))
                .thenReturn(Optional.empty());

        var result =
                service.login(
                        SocialProvider.KAKAO,
                        new SocialIdentityClient.SocialIdentity(
                                "k-10", "user@kakao.com", "abcdefghijklmnopqrstuvwxyz"));

        assertThat(result.response().getSignup().suggestedNickname())
                .isEqualTo("abcdefghijklmn");
    }

    @Test
    void rejectsCompleteWithoutSignupToken() {
        var request = new CompleteSocialSignupRequest("runner", null, true, true, true, false);

        assertThatThrownBy(() -> service.complete(null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_SIGNUP_SESSION_INVALID));
        assertThatThrownBy(() -> service.complete("   ", request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_SIGNUP_SESSION_INVALID));
        verify(signupSessionStore, never()).consume(any());
    }

    @Test
    void rejectsCompleteWithDuplicatedNicknameBeforeConsumingSession() {
        when(memberRepository.existsByNickname("runner")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        "signup-token",
                                        new CompleteSocialSignupRequest(
                                                "runner", null, true, true, true, false)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
        verify(signupSessionStore, never()).consume(any());
    }

    @Test
    void rejectsCompleteWhenSignupSessionIsExpiredOrAlreadyUsed() {
        when(signupSessionStore.consume(any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        "signup-token",
                                        new CompleteSocialSignupRequest(
                                                "runner", null, true, true, true, false)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_SIGNUP_SESSION_INVALID));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void rejectsCompleteWhenEmailWasRegisteredWhileSessionWasOpen() {
        when(signupSessionStore.consume(any()))
                .thenReturn(
                        Optional.of(
                                new SocialSignupSessionStore.SocialSignupSession(
                                        SocialProvider.KAKAO, "k-1", "user@example.com", "member")));
        when(memberRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        "signup-token",
                                        new CompleteSocialSignupRequest(
                                                "runner", null, true, true, true, false)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_REAUTH_REQUIRED));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void rejectsCompleteWhenProviderAccountWasLinkedWhileSessionWasOpen() {
        Member other = Member.createSocial("other@example.com", "other");
        when(signupSessionStore.consume(any()))
                .thenReturn(
                        Optional.of(
                                new SocialSignupSessionStore.SocialSignupSession(
                                        SocialProvider.KAKAO, "k-1", "user@example.com", "member")));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-1"))
                .thenReturn(
                        Optional.of(
                                SocialAccount.link(
                                        other, SocialProvider.KAKAO, "k-1", "user@example.com")));

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        "signup-token",
                                        new CompleteSocialSignupRequest(
                                                "runner", null, true, true, true, false)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_CONFLICT));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void translatesConcurrentLinkViolationIntoConflictWhileCompletingSignup() {
        when(signupSessionStore.consume(any()))
                .thenReturn(
                        Optional.of(
                                new SocialSignupSessionStore.SocialSignupSession(
                                        SocialProvider.KAKAO, "k-2", "user@example.com", "member")));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-2"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(
                        invocation -> {
                            Member member = invocation.getArgument(0);
                            ReflectionTestUtils.setField(member, "id", 5L);
                            return member;
                        });
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate provider_user_id"));

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        "signup-token",
                                        new CompleteSocialSignupRequest(
                                                "runner", null, true, true, true, false)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_CONFLICT));
        verify(authService, never()).issueTokens(any());
    }

    @Test
    void linksProviderAccountToActiveMember() {
        Member member = Member.createLocal("user@example.com", "encoded", "member", null);
        ReflectionTestUtils.setField(member, "id", 2L);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "g-7"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .thenAnswer(
                        invocation -> {
                            SocialAccount saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 11L);
                            return saved;
                        });

        var response =
                service.link(
                        2L,
                        SocialProvider.GOOGLE,
                        new SocialIdentityClient.SocialIdentity(
                                "g-7", " User@Example.com ", "member"));

        assertThat(response.getSocialAccountId()).isEqualTo(11L);
        assertThat(response.getProvider()).isEqualTo("GOOGLE");
        assertThat(response.getProviderEmail()).isEqualTo("user@example.com");
        assertThat(response.getConnectedAt()).isNotNull();
    }

    @Test
    void rejectsLinkForUnknownMember() {
        when(memberRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.link(
                                        9L,
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-7", "user@example.com", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    void rejectsLinkForMemberPendingWithdrawal() {
        Member member = Member.createLocal("user@example.com", "encoded", "member", null);
        ReflectionTestUtils.setField(member, "id", 2L);
        ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWAL_PENDING);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(
                        () ->
                                service.link(
                                        2L,
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-7", "user@example.com", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_ACTIVE));
        verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    void rejectsLinkWhenProviderIsAlreadyConnectedToTheSameMember() {
        Member member = Member.createLocal("user@example.com", "encoded", "member", null);
        ReflectionTestUtils.setField(member, "id", 2L);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(socialAccountRepository.existsByMemberIdAndProvider(2L, SocialProvider.GOOGLE))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.link(
                                        2L,
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-7", "user@example.com", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_CONFLICT));
        verify(socialAccountRepository, never()).findByProviderAndProviderUserId(any(), any());
        verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    void rejectsLinkWhenProviderAccountBelongsToAnotherMember() {
        Member member = Member.createLocal("user@example.com", "encoded", "member", null);
        ReflectionTestUtils.setField(member, "id", 2L);
        Member other = Member.createSocial("other@example.com", "other");
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "g-8"))
                .thenReturn(
                        Optional.of(
                                SocialAccount.link(
                                        other,
                                        SocialProvider.GOOGLE,
                                        "g-8",
                                        "other@example.com")));

        assertThatThrownBy(
                        () ->
                                service.link(
                                        2L,
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-8", "user@example.com", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_CONFLICT));
        verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    void rejectsLinkWhenProviderReturnsMalformedEmail() {
        Member member = Member.createLocal("user@example.com", "encoded", "member", null);
        ReflectionTestUtils.setField(member, "id", 2L);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "g-7"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.link(
                                        2L,
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-7", "missing-at-sign", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_AUTH_FAILED));
        verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    void translatesConcurrentLinkViolationIntoConflictWhileLinking() {
        Member member = Member.createLocal("user@example.com", "encoded", "member", null);
        ReflectionTestUtils.setField(member, "id", 2L);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "g-7"))
                .thenReturn(Optional.empty());
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate provider_user_id"));

        assertThatThrownBy(
                        () ->
                                service.link(
                                        2L,
                                        SocialProvider.GOOGLE,
                                        new SocialIdentityClient.SocialIdentity(
                                                "g-7", "user@example.com", "member")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_CONFLICT));
    }
}
