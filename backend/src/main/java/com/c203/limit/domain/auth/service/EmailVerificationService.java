package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.dto.response.EmailVerificationResponse;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private final MemberRepository members;
    private final EmailVerificationTokenStore tokens;
    private final VerificationEmailSender sender;
    private final AuthRateLimitStore rateLimits;
    private final Duration ttl;
    private final String verificationUrl;
    private final int maxAttempts;
    private final Duration rateLimitWindow;

    public EmailVerificationService(
            MemberRepository members,
            EmailVerificationTokenStore tokens,
            VerificationEmailSender sender,
            AuthRateLimitStore rateLimits,
            @Value("${limit.auth.email-verification.ttl:15m}") Duration ttl,
            @Value("${limit.auth.email-verification.verify-url}") String verificationUrl,
            @Value("${limit.auth.rate-limit.email-verification.max-attempts:3}") int maxAttempts,
            @Value("${limit.auth.rate-limit.email-verification.window:10m}")
                    Duration rateLimitWindow) {
        this.members = members;
        this.tokens = tokens;
        this.sender = sender;
        this.rateLimits = rateLimits;
        this.ttl = ttl;
        this.verificationUrl = verificationUrl;
        this.maxAttempts = maxAttempts;
        this.rateLimitWindow = rateLimitWindow;
    }

    public void request(String email) {
        if (!sender.isAvailable())
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE);
        String normalized = normalize(email);
        if (!rateLimits.tryAcquire(
                "email-verification:" + OpaqueTokenSupport.hash(normalized),
                maxAttempts,
                rateLimitWindow)) {
            log.warn("Email verification request rate limited");
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }
        members.findByEmailIgnoreCase(normalized)
                .filter(member -> member.getEmailVerifiedAt() == null)
                .ifPresent(
                        member -> {
                            String rawToken = newToken();
                            tokens.save(hash(rawToken), member.getId(), ttl);
                            sender.send(
                                    member.getEmail(), verificationUrl + "?token=" + rawToken, ttl);
                            log.info("Email verification message dispatched");
                        });
    }

    @Transactional
    public EmailVerificationResponse verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank())
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
        Long memberId =
                tokens.consume(hash(rawToken))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));
        var member =
                members.findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.verifyEmail();
        log.info("Email verification completed");
        return new EmailVerificationResponse(true, member.getEmailVerifiedAt());
    }

    private String normalize(String email) {
        if (email == null || email.isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
