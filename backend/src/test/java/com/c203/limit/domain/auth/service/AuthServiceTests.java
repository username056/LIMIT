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
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock MemberRepository memberRepository;
    AuthService authService;
    BCryptPasswordEncoder encoder;

    @BeforeEach void setUp() {
        encoder = new BCryptPasswordEncoder(4);
        var provider = new JwtTokenProvider(new ObjectMapper(), "unit-test-secret-with-at-least-32-bytes", java.time.Duration.ofMinutes(30), java.time.Duration.ofDays(14));
        authService = new AuthService(memberRepository, encoder, provider, new InMemoryRefreshTokenStore());
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
        ReflectionTestUtils.setField(member, "id", 1L);
        when(memberRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));
        var response = authService.login(new LoginRequest("user@example.com", "Password123"));
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
    }

    @Test void rejectsWrongPasswordWithoutLeakingAccountDetails() {
        Member member = Member.createLocal("user@example.com", encoder.encode("Password123"), "openrunner", null);
        when(memberRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
}
