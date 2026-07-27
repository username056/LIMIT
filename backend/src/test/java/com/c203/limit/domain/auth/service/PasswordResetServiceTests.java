package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTests {
    @Mock MemberRepository members;
    @Mock AuthService authService;
    @Mock PasswordEncoder passwordEncoder;

    InMemoryPasswordResetTokenStore tokens;
    InMemoryAuthRateLimitStore rateLimits;
    CapturingSender sender;
    PasswordResetService service;

    @BeforeEach
    void setUp() {
        tokens = new InMemoryPasswordResetTokenStore();
        rateLimits = new InMemoryAuthRateLimitStore();
        sender = new CapturingSender();
        service =
                new PasswordResetService(
                        members,
                        tokens,
                        sender,
                        rateLimits,
                        authService,
                        passwordEncoder,
                        Duration.ofMinutes(15),
                        "https://l1mit.shop/reset-password",
                        3,
                        Duration.ofMinutes(10));
    }

    @Test
    void sendsOneTimeTokenAndResetsLocalMemberPassword() {
        Member member =
                Member.createLocal("user@example.com", "encoded-old", "runner", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(authService.normalizeEmail(" User@Example.com ")).thenReturn("user@example.com");
        when(members.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("NewPassword456", "encoded-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword456")).thenReturn("encoded-new");

        service.request(" User@Example.com ");
        String rawToken = URI.create(sender.url).getQuery().substring("token=".length());
        service.reset(rawToken, "NewPassword456");

        assertThat(member.getPassword()).isEqualTo("encoded-new");
        verify(authService).revokeAll(1L);
        assertThatThrownBy(() -> service.reset(rawToken, "AnotherPassword789"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    @Test
    void keepsUnknownEmailResponseIndistinguishable() {
        when(authService.normalizeEmail("missing@example.com"))
                .thenReturn("missing@example.com");
        when(members.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.request("missing@example.com");

        assertThat(sender.url).isNull();
    }

    @Test
    void rejectsPasswordResetRequestsOverConfiguredRateLimit() {
        service =
                new PasswordResetService(
                        members,
                        tokens,
                        sender,
                        rateLimits,
                        authService,
                        passwordEncoder,
                        Duration.ofMinutes(15),
                        "https://l1mit.shop/reset-password",
                        1,
                        Duration.ofMinutes(10));
        when(authService.normalizeEmail("user@example.com")).thenReturn("user@example.com");

        service.request("user@example.com");

        assertThatThrownBy(() -> service.request("user@example.com"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.TOO_MANY_REQUEST));
    }

    private static final class CapturingSender implements PasswordResetEmailSender {
        private String url;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void send(String recipient, String resetUrl, Duration expiresIn) {
            url = resetUrl;
        }
    }
}
