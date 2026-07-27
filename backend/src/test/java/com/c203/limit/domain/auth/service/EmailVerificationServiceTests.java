package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTests {
    @Mock MemberRepository members;
    InMemoryEmailVerificationTokenStore tokens;
    InMemoryAuthRateLimitStore rateLimits;
    CapturingSender sender;
    EmailVerificationService service;

    @BeforeEach
    void setUp() {
        tokens = new InMemoryEmailVerificationTokenStore();
        rateLimits = new InMemoryAuthRateLimitStore();
        sender = new CapturingSender();
        service =
                new EmailVerificationService(
                        members,
                        tokens,
                        sender,
                        rateLimits,
                        Duration.ofMinutes(15),
                        "http://localhost:5173/verify-email",
                        3,
                        Duration.ofMinutes(10));
    }

    @Test
    void sendsOneTimeTokenAndVerifiesMember() {
        Member member = Member.createLocal("user@example.com", "encoded", "runner", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(members.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(member));
        when(members.findById(1L)).thenReturn(Optional.of(member));

        service.request(" User@Example.com ");
        String rawToken = sender.url.substring(sender.url.indexOf("?token=") + 7);
        var response = service.verify(rawToken);

        assertThat(response.emailVerified()).isTrue();
        assertThat(member.getEmailVerifiedAt()).isNotNull();
        assertThatThrownBy(() -> service.verify(rawToken))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));
    }

    @Test
    void refusesRequestsWhenDeliveryIsDisabled() {
        service =
                new EmailVerificationService(
                        members,
                        tokens,
                        new DisabledVerificationEmailSender(),
                        rateLimits,
                        Duration.ofMinutes(15),
                        "http://localhost:5173/verify-email",
                        3,
                        Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.request("user@example.com"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE));
    }

    @Test
    void rejectsRequestsOverConfiguredRateLimit() {
        service =
                new EmailVerificationService(
                        members,
                        tokens,
                        sender,
                        rateLimits,
                        Duration.ofMinutes(15),
                        "http://localhost:5173/verify-email",
                        1,
                        Duration.ofMinutes(10));

        service.request("user@example.com");

        assertThatThrownBy(() -> service.request("user@example.com"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.TOO_MANY_REQUEST));
    }

    private static final class CapturingSender implements VerificationEmailSender {
        private String url;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void send(String recipient, String verificationUrl, Duration expiresIn) {
            url = verificationUrl;
        }
    }
}
