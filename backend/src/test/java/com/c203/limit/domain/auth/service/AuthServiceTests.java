package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.c203.limit.domain.auth.dto.request.LoginRequest;
import com.c203.limit.domain.auth.dto.request.SignupRequest;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.entity.MemberStatus;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock MemberRepository memberRepository;
    @Mock TermsAgreementService termsAgreementService;
    @Mock SellerStatusReader sellerStatusReader;
    @Mock MediaUrlResolver mediaUrlResolver;
    AuthService authService;
    BCryptPasswordEncoder encoder;
    JwtTokenProvider provider;
    InMemoryRefreshTokenStore refreshTokens;

    @BeforeEach void setUp() {
        encoder = new BCryptPasswordEncoder(4);
        provider = new JwtTokenProvider(new ObjectMapper(), "unit-test-secret-with-at-least-32-bytes", java.time.Duration.ofMinutes(30), java.time.Duration.ofDays(14));
        refreshTokens = new InMemoryRefreshTokenStore();
        authService =
                new AuthService(
                        memberRepository,
                        encoder,
                        provider,
                        refreshTokens,
                        termsAgreementService,
                        sellerStatusReader,
                        mediaUrlResolver);
    }

    @Test void signsUpWithNormalizedEmailAndEncodedPassword() {
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        var response = authService.signup(new SignupRequest(" User@Example.com ", "Password123", "openrunner", null));
        assertThat(response.getMemberId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("user@example.com");
    }

    @Test void rejectsDuplicatedEmail() {
        when(memberRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);
        assertThatThrownBy(() -> authService.signup(new SignupRequest("user@example.com", "Password123", "openrunner", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED));
    }

    @Test void loginIssuesTokenForActiveMember() {
        Member member = Member.createLocal("user@example.com", encoder.encode("Password123"), "openrunner", null);
        member.verifyEmail();
        ReflectionTestUtils.setField(member, "id", 1L);
        when(memberRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));
        var response = authService.login(new LoginRequest("user@example.com", "Password123"));
        assertThat(response.body().getAccessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void loginReflectsActiveSellerRoleFromDatabaseReader() {
        Member member =
                Member.createLocal(
                        "seller@example.com", encoder.encode("Password123"), "seller", null);
        member.verifyEmail();
        ReflectionTestUtils.setField(member, "id", 2L);
        when(memberRepository.findByEmailIgnoreCase("seller@example.com"))
                .thenReturn(Optional.of(member));
        when(sellerStatusReader.rolesFor(2L)).thenReturn(java.util.Set.of("MEMBER", "SELLER"));
        when(sellerStatusReader.statusOf(2L)).thenReturn("ACTIVE");

        var response =
                authService.login(new LoginRequest("seller@example.com", "Password123"));
        var claims = provider.parse(response.body().getAccessToken(), "access");

        assertThat(claims.roles()).containsExactlyInAnyOrder("MEMBER", "SELLER");
        assertThat(response.body().getMember().getRoles())
                .containsExactlyInAnyOrder("MEMBER", "SELLER");
        assertThat(response.body().getMember().getSellerStatus()).isEqualTo("ACTIVE");
    }

    @Test void rejectsWrongPasswordWithoutLeakingAccountDetails() {
        Member member = Member.createLocal("user@example.com", encoder.encode("Password123"), "openrunner", null);
        when(memberRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test void rejectsLoginBeforeEmailVerification() {
        Member member = Member.createLocal("user@example.com", encoder.encode("Password123"), "openrunner", null);
        when(memberRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "Password123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test void rejectsAdminRefreshTokenOnMemberRefreshEndpoint() {
        var token = provider.issueRefresh(1L, "ADMIN", java.util.Set.of("SUPER_ADMIN"));
        refreshTokens.save(token.tokenId(), 1L, "ADMIN", java.time.Duration.ofDays(1));
        assertThatThrownBy(() -> authService.refresh(token.value()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test void requiresAtLeast32ByteJwtSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(new ObjectMapper(), "too-short", java.time.Duration.ofMinutes(1), java.time.Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsEmailAvailabilityForNormalizedAddress() {
        when(memberRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);

        var response = authService.emailAvailability("  User@Example.COM  ");

        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.isAvailable()).isTrue();
    }

    @Test
    void reportsEmailAsUnavailableWhenAlreadyRegistered() {
        when(memberRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThat(authService.emailAvailability("user@example.com").isAvailable()).isFalse();
    }

    @Test
    void rejectsMalformedEmailBeforeQueryingRepository() {
        assertThatThrownBy(() -> authService.emailAvailability("not-an-email"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> authService.emailAvailability(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(memberRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void reportsNicknameAsUnavailableWhenAlreadyTaken() {
        when(memberRepository.existsByNickname("openrunner")).thenReturn(true);

        var response = authService.nicknameAvailability("openrunner");

        assertThat(response.getNickname()).isEqualTo("openrunner");
        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    void reportsNicknameAsAvailableWhenNotTaken() {
        when(memberRepository.existsByNickname("openrunner")).thenReturn(false);

        assertThat(authService.nicknameAvailability("openrunner").isAvailable()).isTrue();
    }

    @Test
    void rejectsNicknameOutsideAllowedPatternBeforeQueryingRepository() {
        assertThatThrownBy(() -> authService.nicknameAvailability("a"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> authService.validateNickname("bad nickname!"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> authService.validateNickname(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(memberRepository, never()).existsByNickname(any());
    }

    @Test
    void rejectsPasswordWithoutLetterDigitMixOrMinimumLength() {
        assertThatThrownBy(() -> authService.validatePassword("password"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT));
        assertThatThrownBy(() -> authService.validatePassword("Pass1"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT));
        assertThatThrownBy(() -> authService.validatePassword(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT));
    }

    @Test
    void rejectsDuplicatedNicknameOnSignup() {
        when(memberRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(memberRepository.existsByNickname("openrunner")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("user@example.com", "Password123", "openrunner", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void rejectsSignupWhenRequiredTermsAreNotAccepted() {
        doThrow(new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_ACCEPTED))
                .when(termsAgreementService)
                .validate(any(TermsAgreementService.TermsConsent.class));

        assertThatThrownBy(
                        () ->
                                authService.signup(
                                        new SignupRequest(
                                                "user@example.com",
                                                "Password123",
                                                "openrunner",
                                                null,
                                                false,
                                                true,
                                                true,
                                                false)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_ACCEPTED));
        verify(memberRepository, never()).save(any(Member.class));
        verify(termsAgreementService, never()).record(any(), any());
    }

    @Test
    void rejectsLoginForUnknownEmail() {
        when(memberRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "Password123")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsPasswordLoginForSocialOnlyMemberWithoutStoredPassword() {
        Member member = Member.createSocial("social@example.com", "socialuser");
        when(memberRepository.findByEmailIgnoreCase("social@example.com")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest("social@example.com", "Password123")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsLoginForSuspendedMemberBeforeIssuingTokens() {
        Member member = Member.createLocal("user@example.com", encoder.encode("Password123"), "openrunner", null);
        member.verifyEmail();
        ReflectionTestUtils.setField(member, "status", MemberStatus.SUSPENDED);
        when(memberRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "Password123")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_ACTIVE));
        assertThat(member.getLastLoginAt()).isNull();
    }

    @Test
    void refreshRotatesRefreshTokenAndRevokesThePreviousOne() {
        Member member = Member.createLocal("user@example.com", encoder.encode("Password123"), "openrunner", null);
        member.verifyEmail();
        ReflectionTestUtils.setField(member, "id", 1L);
        var token = provider.issueRefresh(1L, "MEMBER", java.util.Set.of("MEMBER"));
        refreshTokens.save(token.tokenId(), 1L, "MEMBER", java.time.Duration.ofDays(1));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(sellerStatusReader.rolesFor(1L)).thenReturn(java.util.Set.of("MEMBER"));

        var result = authService.refresh(token.value());

        assertThat(result.body().getAccessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo(token.value());
        assertThat(refreshTokens.isValid(token.tokenId(), 1L, "MEMBER")).isFalse();
        var rotated = provider.parse(result.refreshToken(), "refresh");
        assertThat(refreshTokens.isValid(rotated.tokenId(), 1L, "MEMBER")).isTrue();
    }

    @Test
    void rejectsRefreshTokenThatIsNotRegisteredInTheStore() {
        var token = provider.issueRefresh(1L, "MEMBER", java.util.Set.of("MEMBER"));

        assertThatThrownBy(() -> authService.refresh(token.value()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REVOKED_TOKEN));
    }

    @Test
    void rejectsRefreshWhenMemberRecordNoLongerExists() {
        var token = provider.issueRefresh(5L, "MEMBER", java.util.Set.of("MEMBER"));
        refreshTokens.save(token.tokenId(), 5L, "MEMBER", java.time.Duration.ofDays(1));
        when(memberRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(token.value()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        assertThat(refreshTokens.isValid(token.tokenId(), 5L, "MEMBER")).isTrue();
    }

    @Test
    void rejectsRefreshForWithdrawnMemberWithoutRevokingTheToken() {
        Member member = Member.createLocal("user@example.com", encoder.encode("Password123"), "openrunner", null);
        ReflectionTestUtils.setField(member, "id", 6L);
        ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWN);
        var token = provider.issueRefresh(6L, "MEMBER", java.util.Set.of("MEMBER"));
        refreshTokens.save(token.tokenId(), 6L, "MEMBER", java.time.Duration.ofDays(1));
        when(memberRepository.findById(6L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.refresh(token.value()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_ACTIVE));
    }

    @Test
    void logoutRevokesOnlyTheSubmittedRefreshToken() {
        var first = provider.issueRefresh(1L, "MEMBER", java.util.Set.of("MEMBER"));
        var second = provider.issueRefresh(1L, "MEMBER", java.util.Set.of("MEMBER"));
        refreshTokens.save(first.tokenId(), 1L, "MEMBER", java.time.Duration.ofDays(1));
        refreshTokens.save(second.tokenId(), 1L, "MEMBER", java.time.Duration.ofDays(1));

        authService.logout(first.value());

        assertThat(refreshTokens.isValid(first.tokenId(), 1L, "MEMBER")).isFalse();
        assertThat(refreshTokens.isValid(second.tokenId(), 1L, "MEMBER")).isTrue();
    }

    @Test
    void logoutRejectsTamperedRefreshToken() {
        assertThatThrownBy(() -> authService.logout("not.a.token"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void revokeAllDropsEveryMemberSessionButKeepsOtherAccountTypes() {
        var memberToken = provider.issueRefresh(1L, "MEMBER", java.util.Set.of("MEMBER"));
        var otherMemberToken = provider.issueRefresh(1L, "MEMBER", java.util.Set.of("MEMBER"));
        var adminToken = provider.issueRefresh(1L, "ADMIN", java.util.Set.of("SUPER_ADMIN"));
        refreshTokens.save(memberToken.tokenId(), 1L, "MEMBER", java.time.Duration.ofDays(1));
        refreshTokens.save(otherMemberToken.tokenId(), 1L, "MEMBER", java.time.Duration.ofDays(1));
        refreshTokens.save(adminToken.tokenId(), 1L, "ADMIN", java.time.Duration.ofDays(1));

        authService.revokeAll(1L);

        assertThat(refreshTokens.isValid(memberToken.tokenId(), 1L, "MEMBER")).isFalse();
        assertThat(refreshTokens.isValid(otherMemberToken.tokenId(), 1L, "MEMBER")).isFalse();
        assertThat(refreshTokens.isValid(adminToken.tokenId(), 1L, "ADMIN")).isTrue();
    }

    @Test
    void issueTokensBuildsSummaryFromSellerStatusAndResolvedProfileImage() {
        Member member = Member.createSocial("user@example.com", "openrunner");
        ReflectionTestUtils.setField(member, "id", 3L);
        ReflectionTestUtils.setField(member, "profileImageKey", "members/3/profile/a.webp");
        when(sellerStatusReader.rolesFor(3L)).thenReturn(java.util.Set.of("MEMBER", "SELLER"));
        when(sellerStatusReader.statusOf(3L)).thenReturn("PENDING");
        when(mediaUrlResolver.resolve("members/3/profile/a.webp", null))
                .thenReturn("https://cdn.example.test/members/3/profile/a.webp");

        var result = authService.issueTokens(member);

        assertThat(result.body().getTokenType()).isEqualTo("Bearer");
        assertThat(result.body().getExpiresIn()).isEqualTo(1800L);
        assertThat(result.body().getMember().getSellerStatus()).isEqualTo("PENDING");
        assertThat(result.body().getMember().getProfileImageUrl())
                .isEqualTo("https://cdn.example.test/members/3/profile/a.webp");
        var claims = provider.parse(result.refreshToken(), "refresh");
        assertThat(refreshTokens.isValid(claims.tokenId(), 3L, "MEMBER")).isTrue();
    }
}
