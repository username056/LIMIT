package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.auth.client.SocialIdentityClient;
import com.c203.limit.domain.auth.client.SocialIdentityClient.SocialIdentity;
import com.c203.limit.domain.auth.dto.request.CompleteSocialSignupRequest;
import com.c203.limit.domain.auth.dto.response.SocialAccountResponse;
import com.c203.limit.domain.auth.entity.SocialAccount;
import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.repository.SocialAccountRepository;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 제공자 문자열 해석(대소문자/미지원 값), 등록되지 않은 클라이언트, state 검증 위임 순서, 응답 이메일 마스킹, 마지막 로그인 수단 보호 등 SocialAuthService가
 * 직접 책임지는 분기를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTests {

    private static final String REDIRECT_URI = "https://limit.example.com/callback";
    private static final LocalDateTime LINKED_AT = LocalDateTime.of(2026, 7, 1, 10, 0);

    @Mock SocialIdentityClient googleClient;
    @Mock SocialAccountRepository socialAccountRepository;
    @Mock SocialAccountLoginService socialAccountLoginService;
    @Mock OAuthAuthorizationService authorizationService;

    SocialAuthService service;

    @BeforeEach
    void setUp() {
        when(googleClient.provider()).thenReturn(SocialProvider.GOOGLE);
        service =
                new SocialAuthService(
                        List.of(googleClient),
                        socialAccountRepository,
                        socialAccountLoginService,
                        authorizationService);
    }

    private SocialAccount linkedAccount(Member member, String providerEmail) {
        SocialAccount account =
                SocialAccount.link(member, SocialProvider.GOOGLE, "g-1", providerEmail);
        ReflectionTestUtils.setField(account, "id", 11L);
        ReflectionTestUtils.setField(account, "linkedAt", LINKED_AT);
        return account;
    }

    @Test
    void loginValidatesTheOauthStateBeforeExchangingTheAuthorizationCode() {
        SocialIdentity identity = new SocialIdentity("g-1", "user@example.com", "runner");
        SocialAccountLoginService.SocialLoginResult expected =
                new SocialAccountLoginService.SocialLoginResult(null, "refresh", null);
        when(googleClient.exchange("code", REDIRECT_URI, "state")).thenReturn(identity);
        when(socialAccountLoginService.login(SocialProvider.GOOGLE, identity))
                .thenReturn(expected);

        var result = service.login("GOOGLE", "code", REDIRECT_URI, "state", "cookie-state");

        assertThat(result).isSameAs(expected);
        verify(authorizationService)
                .consume(SocialProvider.GOOGLE, REDIRECT_URI, "state", "cookie-state");
    }

    @Test
    void loginAcceptsALowerCaseProviderPathVariable() {
        SocialIdentity identity = new SocialIdentity("g-1", "user@example.com", "runner");
        when(googleClient.exchange("code", REDIRECT_URI, "state")).thenReturn(identity);
        when(socialAccountLoginService.login(SocialProvider.GOOGLE, identity))
                .thenReturn(new SocialAccountLoginService.SocialLoginResult(null, "refresh", null));

        assertThat(service.login("google", "code", REDIRECT_URI, "state", "cookie-state"))
                .isNotNull();
    }

    @Test
    void loginRejectsAProviderValueThatIsNotAKnownEnumConstant() {
        assertThatThrownBy(
                        () ->
                                service.login(
                                        "facebook", "code", REDIRECT_URI, "state", "cookie-state"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);

        verifyNoInteractions(authorizationService, socialAccountLoginService);
    }

    @Test
    void loginRejectsAKnownProviderThatHasNoRegisteredClient() {
        assertThatThrownBy(
                        () ->
                                service.login(
                                        "NAVER", "code", REDIRECT_URI, "state", "cookie-state"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);

        verifyNoInteractions(authorizationService, socialAccountLoginService);
    }

    @Test
    void completeSignupDelegatesTheSignupTokenAndRequestUnchanged() {
        CompleteSocialSignupRequest request =
                new CompleteSocialSignupRequest("runner", "01012345678", true, true, true, false);
        SocialAccountLoginService.SocialLoginResult expected =
                new SocialAccountLoginService.SocialLoginResult(null, "refresh", null);
        when(socialAccountLoginService.complete("signup-token", request)).thenReturn(expected);

        assertThat(service.completeSignup("signup-token", request)).isSameAs(expected);
    }

    @Test
    void linkValidatesTheLinkStateForTheMemberAndMasksTheProviderEmailInTheResponse() {
        SocialIdentity identity = new SocialIdentity("g-1", "user@example.com", "runner");
        when(googleClient.exchange("code", REDIRECT_URI, "state")).thenReturn(identity);
        when(socialAccountLoginService.link(42L, SocialProvider.GOOGLE, identity))
                .thenReturn(
                        new SocialAccountResponse(
                                11L, "GOOGLE", "user@example.com", LINKED_AT));

        SocialAccountResponse response =
                service.link(42L, "GOOGLE", "code", REDIRECT_URI, "state", "cookie-state");

        assertThat(response.getSocialAccountId()).isEqualTo(11L);
        assertThat(response.getProvider()).isEqualTo("GOOGLE");
        assertThat(response.getProviderEmail()).isEqualTo("u***@example.com");
        assertThat(response.getConnectedAt()).isEqualTo(LINKED_AT);
        verify(authorizationService)
                .consumeLink(SocialProvider.GOOGLE, REDIRECT_URI, "state", "cookie-state", 42L);
    }

    @Test
    void linkFullyMasksASingleCharacterLocalPart() {
        SocialIdentity identity = new SocialIdentity("g-1", "a@example.com", "runner");
        when(googleClient.exchange("code", REDIRECT_URI, "state")).thenReturn(identity);
        when(socialAccountLoginService.link(42L, SocialProvider.GOOGLE, identity))
                .thenReturn(new SocialAccountResponse(11L, "GOOGLE", "a@example.com", LINKED_AT));

        assertThat(
                        service.link(
                                        42L,
                                        "GOOGLE",
                                        "code",
                                        REDIRECT_URI,
                                        "state",
                                        "cookie-state")
                                .getProviderEmail())
                .isEqualTo("***@example.com");
    }

    @Test
    void linkKeepsAMissingProviderEmailAsNull() {
        SocialIdentity identity = new SocialIdentity("g-1", null, "runner");
        when(googleClient.exchange("code", REDIRECT_URI, "state")).thenReturn(identity);
        when(socialAccountLoginService.link(42L, SocialProvider.GOOGLE, identity))
                .thenReturn(new SocialAccountResponse(11L, "GOOGLE", null, LINKED_AT));

        assertThat(
                        service.link(
                                        42L,
                                        "GOOGLE",
                                        "code",
                                        REDIRECT_URI,
                                        "state",
                                        "cookie-state")
                                .getProviderEmail())
                .isNull();
    }

    @Test
    void linkRejectsAProviderValueThatIsNotAKnownEnumConstant() {
        assertThatThrownBy(
                        () ->
                                service.link(
                                        42L,
                                        "facebook",
                                        "code",
                                        REDIRECT_URI,
                                        "state",
                                        "cookie-state"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);

        verifyNoInteractions(authorizationService, socialAccountLoginService);
    }

    @Test
    void linkRejectsAKnownProviderThatHasNoRegisteredClient() {
        assertThatThrownBy(
                        () ->
                                service.link(
                                        42L,
                                        "kakao",
                                        "code",
                                        REDIRECT_URI,
                                        "state",
                                        "cookie-state"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);

        verifyNoInteractions(authorizationService, socialAccountLoginService);
    }

    @Test
    void accountsMasksEveryLinkedProviderEmail() {
        Member member = Member.createSocial("user@example.com", "runner");
        when(socialAccountRepository.findAllByMemberId(42L))
                .thenReturn(List.of(linkedAccount(member, "user@example.com")));

        assertThat(service.accounts(42L))
                .singleElement()
                .satisfies(
                        account -> {
                            assertThat(account.getSocialAccountId()).isEqualTo(11L);
                            assertThat(account.getProvider()).isEqualTo("GOOGLE");
                            assertThat(account.getProviderEmail()).isEqualTo("u***@example.com");
                            assertThat(account.getConnectedAt()).isEqualTo(LINKED_AT);
                        });
    }

    @Test
    void accountsFullyMasksAValueThatDoesNotLookLikeAnEmail() {
        Member member = Member.createSocial("user@example.com", "runner");
        when(socialAccountRepository.findAllByMemberId(42L))
                .thenReturn(List.of(linkedAccount(member, "no-at-sign")));

        assertThat(service.accounts(42L).get(0).getProviderEmail()).isEqualTo("***");
    }

    @Test
    void accountsReturnsAnEmptyListWhenNothingIsLinked() {
        when(socialAccountRepository.findAllByMemberId(42L)).thenReturn(List.of());

        assertThat(service.accounts(42L)).isEmpty();
    }

    @Test
    void unlinkRejectsAnAccountThatDoesNotBelongToTheMember() {
        when(socialAccountRepository.findByIdAndMemberId(11L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlink(42L, 11L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND);

        verify(socialAccountRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkRefusesToRemoveTheLastLoginMethodOfAPasswordlessMember() {
        Member member = Member.createSocial("user@example.com", "runner");
        SocialAccount account = linkedAccount(member, "user@example.com");
        when(socialAccountRepository.findByIdAndMemberId(11L, 42L))
                .thenReturn(Optional.of(account));
        when(socialAccountRepository.countByMemberId(42L)).thenReturn(1L);

        assertThatThrownBy(() -> service.unlink(42L, 11L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LAST_LOGIN_METHOD);

        verify(socialAccountRepository, never()).delete(account);
    }

    @Test
    void unlinkRemovesTheAccountWhenAnotherSocialAccountRemains() {
        Member member = Member.createSocial("user@example.com", "runner");
        SocialAccount account = linkedAccount(member, "user@example.com");
        when(socialAccountRepository.findByIdAndMemberId(11L, 42L))
                .thenReturn(Optional.of(account));
        when(socialAccountRepository.countByMemberId(42L)).thenReturn(2L);

        service.unlink(42L, 11L);

        verify(socialAccountRepository).delete(account);
    }

    @Test
    void unlinkRemovesTheOnlySocialAccountWhenTheMemberStillHasAPassword() {
        Member member =
                Member.createLocal("user@example.com", "encoded", "runner", "01012345678");
        SocialAccount account = linkedAccount(member, "user@example.com");
        when(socialAccountRepository.findByIdAndMemberId(11L, 42L))
                .thenReturn(Optional.of(account));

        service.unlink(42L, 11L);

        // 비밀번호가 있으면 남은 소셜 계정 수를 셀 필요가 없다.
        verify(socialAccountRepository, never()).countByMemberId(42L);
        verify(socialAccountRepository).delete(account);
    }
}
