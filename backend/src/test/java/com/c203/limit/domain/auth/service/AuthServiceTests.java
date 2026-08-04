package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
