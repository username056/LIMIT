package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.entity.MemberStatus;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final MemberRepository members;
    private final PasswordResetTokenStore tokens;
    private final PasswordResetEmailSender sender;
    private final AuthRateLimitStore rateLimits;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final Duration ttl;
    private final String resetUrl;
    private final int maxAttempts;
    private final Duration rateLimitWindow;

    public PasswordResetService(
            MemberRepository members,
            PasswordResetTokenStore tokens,
            PasswordResetEmailSender sender,
            AuthRateLimitStore rateLimits,
            AuthService authService,
            PasswordEncoder passwordEncoder,
            @Value("${limit.auth.password-reset.ttl:15m}") Duration ttl,
            @Value("${limit.auth.password-reset.reset-url}") String resetUrl,
            @Value("${limit.auth.rate-limit.password-reset.max-attempts:3}") int maxAttempts,
            @Value("${limit.auth.rate-limit.password-reset.window:10m}") Duration rateLimitWindow) {
        this.members = members;
        this.tokens = tokens;
        this.sender = sender;
        this.rateLimits = rateLimits;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.ttl = ttl;
        this.resetUrl = resetUrl;
        this.maxAttempts = maxAttempts;
        this.rateLimitWindow = rateLimitWindow;
    }

    public void request(String email) {
        if (!sender.isAvailable()) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_UNAVAILABLE);
        }
        String normalized = authService.normalizeEmail(email);
        if (!rateLimits.tryAcquire(
                "password-reset:" + OpaqueTokenSupport.hash(normalized),
                maxAttempts,
                rateLimitWindow)) {
            log.warn("Password reset request rate limited");
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }
        members.findByEmailIgnoreCase(normalized)
                .filter(this::canResetPassword)
                .ifPresent(
                        member -> {
                            String rawToken = OpaqueTokenSupport.generate();
                            tokens.save(OpaqueTokenSupport.hash(rawToken), member.getId(), ttl);
                            sender.send(
                                    member.getEmail(),
                                    UriComponentsBuilder.fromUriString(resetUrl)
                                            .queryParam("token", rawToken)
                                            .build()
                                            .encode()
                                            .toUriString(),
                                    ttl);
                            log.info("Password reset message dispatched");
                        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        authService.validatePassword(newPassword);
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        Long memberId =
                tokens.consume(OpaqueTokenSupport.hash(rawToken))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        Member member =
                members.findById(memberId)
                        .filter(this::canResetPassword)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        member.changePassword(passwordEncoder.encode(newPassword));
        authService.revokeAll(memberId);
        log.info("Password reset completed and member refresh tokens revoked");
    }

    private boolean canResetPassword(Member member) {
        return member.getPassword() != null && member.getStatus() == MemberStatus.ACTIVE;
    }
}
